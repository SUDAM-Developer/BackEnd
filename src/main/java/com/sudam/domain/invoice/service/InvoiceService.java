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
 * 수입 탭 입금 현황 조회와 선생님 입금 확인.
 * 두 API가 같은 한 줄 변환(toItems → InvoiceItemResponse.of)을 쓴다.
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

    // 두 API가 공유하는 한 줄 변환. 공유 설정은 대상 과외 것만 한 번에 가져온다.
    private List<InvoiceItemResponse> toItems(Collection<Invoice> targets, Collection<Invoice> unsettled) {
        Set<Long> tutoringIds = targets.stream()
                .map(invoice -> invoice.getTutoring().getId())
                .collect(Collectors.toSet());
        Map<Long, Boolean> reminderEnabledByTutoring = tutoringSettingRepository.findAllById(tutoringIds).stream()
                .collect(Collectors.toMap(TutoringSetting::getTutoringId, TutoringSetting::isUnpaidReminderEnabled));

        return targets.stream()
                .map(invoice -> InvoiceItemResponse.of(
                        invoice,
                        previousUnpaidAmount(invoice, unsettled),
                        reminderEnabledByTutoring.getOrDefault(invoice.getTutoring().getId(), false)))
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
        return Objects.equals(tutoring.getTeacherId(), teacherId) && !tutoring.isDeleted();
    }

    // '이번 달' 판단 기준: 안내를 보냈으면 안내 시각, 아니면 생성 시각.
    private LocalDateTime announcedAt(Invoice invoice) {
        return invoice.getNotifiedAt() != null ? invoice.getNotifiedAt() : invoice.getCreatedAt();
    }
}
