package com.sudam.domain.invoice.service;

import com.sudam.domain.invoice.dto.request.ConfirmPaymentRequest;
import com.sudam.domain.invoice.dto.response.InvoiceItemResponse;
import com.sudam.domain.invoice.dto.response.InvoiceListResponse;
import com.sudam.domain.invoice.dto.response.PaymentConfirmResponse;
import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.entity.RecordedPayment;
import com.sudam.domain.invoice.enums.InvoiceStatus;
import com.sudam.domain.invoice.exception.InvoiceException;
import com.sudam.domain.invoice.exception.code.InvoiceErrorCode;
import com.sudam.domain.invoice.repository.InvoiceRepository;
import com.sudam.domain.participant.dto.response.ParentResponse;
import com.sudam.domain.participant.repository.ParticipantRepository;
import com.sudam.domain.share.enums.ShareKind;
import com.sudam.domain.share.enums.ShareSourceType;
import com.sudam.domain.share.service.LinkShareRecorder;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringSetting;
import com.sudam.domain.tutoring.repository.TutoringSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 수입 탭 입금 현황 조회, 선생님 입금 확인, 입금 안내 다시 보내기 확인.
 * 세 API가 같은 한 줄 변환(toItems → InvoiceItemResponse.of)을 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceService {

    // 잔액이 남아 있을 수 있는 상태. PAID는 다 받았고 CANCELED는 합계에서 빠진다.
    static final Set<InvoiceStatus> UNSETTLED_STATUSES =
            Set.of(InvoiceStatus.PENDING, InvoiceStatus.PARTIAL, InvoiceStatus.CONFIRM_REQUESTED);

    private final InvoiceRepository invoiceRepository;
    private final TutoringSettingRepository tutoringSettingRepository;
    private final ParticipantRepository participantRepository;
    private final LinkShareRecorder linkShareRecorder;
    private final Clock clock;

    /***
     * 함수 기능: 수입 탭 화면 전체를 만든다. 대상은 내 과외(삭제 안 됨, 종료 포함)의 취소 안 된 청구 중
     *          안내일(notified_at, 없으면 created_at)이 그 달인 것과, 그 달 이전 청구 중 잔액이 남은 것이다.
     * @param teacherId 로그인한 선생님 (users.id)
     * @param month     조회할 달. NULL이면 한국 시간 이번 달
     * @return 정렬된 목록과 그 목록에서 더한 건수 · 요약
     */
    public InvoiceListResponse getInvoices(Long teacherId, YearMonth month) {
        YearMonth target = month != null ? month : YearMonth.now(clock);
        LocalDateTime start = target.atDay(1).atStartOfDay();
        LocalDateTime end = target.plusMonths(1).atDay(1).atStartOfDay();

        List<Invoice> announced = invoiceRepository.findAllOfTeacherAnnouncedBetween(teacherId, start, end);
        List<Invoice> unsettled = invoiceRepository.findAllOfTeacherByStatusIn(teacherId, UNSETTLED_STATUSES);

        // 같은 영속성 컨텍스트라 두 조회에 겹치는 청구는 같은 인스턴스다. ID로 중복을 없앤다.
        Map<Long, Invoice> targets = new LinkedHashMap<>();
        announced.forEach(invoice -> targets.put(invoice.getId(), invoice));
        unsettled.stream()
                .filter(invoice -> announcedAt(invoice).isBefore(start))
                .filter(invoice -> invoice.balance() > 0)
                .forEach(invoice -> targets.putIfAbsent(invoice.getId(), invoice));

        return InvoiceListResponse.from(toItems(targets.values(), unsettled));
    }

    /***
     * 함수 기능: 선생님이 확인한 입금을 반영한다. 청구 행을 잠근 뒤 엔티티의 recordPayment로 처리하고,
     *          반영 후의 한 줄을 목록과 같은 변환으로 돌려준다. 학부모 알림은 보내지 않는다.
     * @param teacherId 로그인한 선생님 (users.id)
     * @param invoiceId 청구 ID
     * @param request   금액 · 확인 시각 (둘 다 선택)
     * @return 새 입금 ID, 반영한 금액, 반영 후의 한 줄
     * @throws InvoiceException 청구가 없거나 내 과외의 청구가 아니면 INVOICE404_1,
     *                          취소된 청구면 INVOICE409_1, 이미 완납이면 INVOICE409_3
     */
    @Transactional
    public PaymentConfirmResponse confirmPayment(Long teacherId, Long invoiceId, ConfirmPaymentRequest request) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .filter(found -> isOwnedBy(found.getTutoring(), teacherId))
                .orElseThrow(() -> new InvoiceException(InvoiceErrorCode.INVOICE_NOT_FOUND));

        LocalDateTime confirmedAt = request.confirmedAt() != null ? request.confirmedAt() : LocalDateTime.now(clock);
        RecordedPayment recorded = invoice.recordPayment(request.amount(), confirmedAt);

        // 입금 행은 cascade(PERSIST)로 flush 때 저장된다. IDENTITY라 flush해야 paymentId가 정해진다.
        invoiceRepository.flush();

        List<Invoice> unsettled =
                invoiceRepository.findAllByTutoringIdAndStatusIn(invoice.getTutoring().getId(), UNSETTLED_STATUSES);
        InvoiceItemResponse item = toItems(List.of(invoice), unsettled).getFirst();

        return new PaymentConfirmResponse(recorded.payment().getId(), recorded.appliedAmount(), item);
    }

    /***
     * 함수 기능: 카카오톡 공유 창으로 입금 안내를 다시 보낸 뒤 호출한다. 다시 보낸 시각을 남기고
     *          바뀐 한 줄(아래 줄 RESENT)을 목록과 같은 변환으로 돌려준다.
     *
     *          전체 흐름 (T5 '안 들어온 교육비 다시 안내하기'):
     *          ① 프론트가 POST /shares (kind = PAYMENT_RESEND, sourceId = 청구 ID, participantId = 카드의 parents)로
     *             카톡 메시지 · 링크를 받는다. 이때 link_shares에 REQUESTED 행이 생긴다 (ShareService).
     *          ② 프론트가 카카오톡 공유 창을 띄우고 선생님이 보낸다. 서버는 이 단계를 알 수 없다 (웹훅은 UT 이후).
     *          ③ 프론트가 이 API를 부른다 → 청구에 다시 보낸 시각을 남기고, ①의 REQUESTED를 SENT로 바꾼다.
     *          공유 창 없이 이 API만 불러도 청구는 바뀐다 (공유 기록만 없을 뿐이다).
     * @param teacherId 로그인한 선생님 (users.id)
     * @param invoiceId 청구 ID
     * @return 다시 보낸 뒤의 교육비 한 줄
     * @throws InvoiceException 청구가 없거나 내 과외의 청구가 아니면 INVOICE404_1,
     *                          취소된 청구면 INVOICE409_1, 이미 입금 완료면 INVOICE409_3
     */
    @Transactional
    public InvoiceItemResponse renotify(Long teacherId, Long invoiceId) {
        // 1. 청구 조회 · 소유 확인: 입금 확인과 동시에 들어오면 늦게 커밋한 쪽이 상태를 덮어쓰므로
        //    청구 행을 잠근다(SELECT … FOR UPDATE). 남의 청구 · 삭제된 과외도 '없음'(INVOICE404_1)으로 응답해
        //    다른 선생님의 청구가 있는지 드러내지 않는다.
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .filter(found -> isOwnedBy(found.getTutoring(), teacherId))
                .orElseThrow(() -> new InvoiceException(InvoiceErrorCode.INVOICE_NOT_FOUND));

        // 2. 다시 보낸 시각 기록: 규칙(취소 · 완납이면 거절, 마지막 시각으로 덮어쓰기)은 엔티티가 지킨다.
        //    변경 감지로 커밋 때 renotified_at이 저장된다.
        LocalDateTime now = LocalDateTime.now(clock);
        invoice.renotify(now);

        // 3. 공유 기록 완료: ①에서 남긴 이 청구의 PAYMENT_RESEND REQUESTED를 SENT(MANUAL)로 바꾼다.
        //    웹훅을 받기 전에는 선생님의 이 확인이 '보냈다'는 유일한 증거다. 같은 트랜잭션이라 2와 함께 커밋 · 롤백된다.
        //    같은 청구의 첫 교육비 안내(FIRST_INVOICE) 공유는 종류가 달라 건드리지 않는다.
        linkShareRecorder.markSentByTeacher(ShareKind.PAYMENT_RESEND, ShareSourceType.INVOICE, invoiceId, now);

        // 4. 응답 한 줄 만들기: 입금 현황 목록과 같은 변환을 써서 프론트가 카드 하나만 바꿔 끼우면 되게 한다.
        //    '앞선 청구 잔액'을 계산하려고 같은 과외의 잔액이 남을 수 있는 청구를 함께 가져온다.
        //    입금 대기(PENDING)면 아래 줄이 RESENT('카톡으로 입금 안내를 다시 보냈어요')가 되고,
        //    확인 대기 · 일부 입금은 statusLine 순서상 그 표시가 먼저 보인다.
        List<Invoice> unsettled =
                invoiceRepository.findAllByTutoringIdAndStatusIn(invoice.getTutoring().getId(), UNSETTLED_STATUSES);
        return toItems(List.of(invoice), unsettled).getFirst();
    }

    // 세 API가 공유하는 한 줄 변환. 공유 설정 · 받는 사람(학부모)은 대상 과외 것만 한 번에 가져온다.
    private List<InvoiceItemResponse> toItems(Collection<Invoice> targets, Collection<Invoice> unsettled) {
        Set<Long> tutoringIds = targets.stream()
                .map(invoice -> invoice.getTutoring().getId())
                .collect(Collectors.toSet());
        Map<Long, Boolean> reminderEnabledByTutoring = tutoringSettingRepository.findAllById(tutoringIds).stream()
                .collect(Collectors.toMap(TutoringSetting::getTutoringId, TutoringSetting::isUnpaidReminderEnabled));
        Map<Long, List<ParentResponse>> parentsByTutoring = participantRepository.findShareableParents(tutoringIds)
                .stream()
                .collect(Collectors.groupingBy(parent -> parent.getTutoring().getId(),
                        Collectors.mapping(ParentResponse::from, Collectors.toList())));

        return targets.stream()
                .map(invoice -> InvoiceItemResponse.of(
                        invoice,
                        previousUnpaidAmount(invoice, unsettled),
                        reminderEnabledByTutoring.getOrDefault(invoice.getTutoring().getId(), false),
                        parentsByTutoring.getOrDefault(invoice.getTutoring().getId(), List.of())))
                .toList();
    }

    // 같은 과외에서 이 청구보다 먼저 만든(ID가 작은) 취소 안 된 청구들의 잔액 합.
    // 잔액이 남을 수 있는 청구는 모두 unsettled에 있으므로 그 안에서만 더하면 된다.
    private int previousUnpaidAmount(Invoice invoice, Collection<Invoice> unsettled) {
        Long tutoringId = invoice.getTutoring().getId();
        return unsettled.stream()
                .filter(other -> Objects.equals(other.getTutoring().getId(), tutoringId))
                .filter(other -> other.getId() < invoice.getId())
                .mapToInt(other -> Math.max(0, other.balance()))
                .sum();
    }

    private boolean isOwnedBy(Tutoring tutoring, Long teacherId) {
        return Objects.equals(tutoring.getTeacher().getId(), teacherId) && !tutoring.isDeleted();
    }

    // '이번 달' 판단 기준: 안내를 보냈으면 안내 시각, 아니면 생성 시각.
    private LocalDateTime announcedAt(Invoice invoice) {
        return invoice.getNotifiedAt() != null ? invoice.getNotifiedAt() : invoice.getCreatedAt();
    }
}
