package com.sudam.domain.share.service;

import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.enums.InvoiceStatus;
import com.sudam.domain.invoice.exception.InvoiceException;
import com.sudam.domain.invoice.exception.code.InvoiceErrorCode;
import com.sudam.domain.invoice.repository.InvoiceRepository;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.NotificationConsent;
import com.sudam.domain.participant.enums.ParticipantRole;
import com.sudam.domain.participant.enums.ReceiveMethod;
import com.sudam.domain.participant.repository.ParticipantRepository;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.exception.ReportException;
import com.sudam.domain.report.exception.code.ReportErrorCode;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.share.dto.request.CreateShareRequest;
import com.sudam.domain.share.dto.response.ShareResponse;
import com.sudam.domain.share.entity.LinkShare;
import com.sudam.domain.share.enums.ShareKind;
import com.sudam.domain.share.enums.ShareSourceType;
import com.sudam.domain.share.exception.ShareException;
import com.sudam.domain.share.exception.code.ShareErrorCode;
import com.sudam.domain.share.repository.LinkShareRepository;
import com.sudam.domain.tutoring.entity.Tutoring;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 카카오톡 공유 정보를 만든다. 선생님이 공유 창을 열기 직전에 불러 메시지(링크 · 제목 · 설명 · 버튼)를 받고,
 * 공유 기록(link_shares)을 REQUESTED로 남긴다. 보낸 것은 원인 데이터의 확인 API가 SENT로 바꾼다 (LinkShareRecorder).
 * 지금은 정산 리포트(SETTLEMENT_REPORT)와 입금 안내 다시 보내기(PAYMENT_RESEND)만 지원한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShareService {

    private static final String REPORT_BUTTON = "링크로 보기";
    private static final String INVOICE_BUTTON = "교육비 보기";
    private static final String SEPARATOR = " · ";

    private final SettlementReportRepository settlementReportRepository;
    private final InvoiceRepository invoiceRepository;
    private final ParticipantRepository participantRepository;
    private final LinkShareRepository linkShareRepository;
    private final ShareLinkFactory shareLinkFactory;
    private final Clock clock;

    /***
     * 함수 기능: 공유 종류에 맞는 메시지를 만들고 공유 기록을 REQUESTED로 남긴다.
     *          같은 원인 · 같은 받는 사람의 이전 REQUESTED는 DISMISSED로 바꾼다 (공유 창을 여러 번 열어도 할 일은 1건).
     * @param teacherId 로그인한 선생님 (users.id)
     * @param request   종류 · 원인 ID · 받는 사람
     * @return 공유 기록 ID와 카카오톡 메시지
     * @throws ShareException   지원하지 않는 종류 SHARE400_1, sourceId 없음 SHARE400_2,
     *                          받는 사람이 그 과외의 링크 있는 학부모가 아니면 SHARE404_1, 수신 거부 SHARE409_1
     * @throws ReportException  리포트가 없거나 남의 리포트 REPORT404_1, 코멘트 확인 전 REPORT409_2
     * @throws InvoiceException 청구가 없거나 남의 청구 INVOICE404_1, 취소 INVOICE409_1, 완납 INVOICE409_3
     */
    @Transactional
    public ShareResponse create(Long teacherId, CreateShareRequest request) {

        // 존재하지 않는 공유 종류(ex 정산 리포트, 입금 안내 등)면 예외를 반환한다.
        if (request.kind() != ShareKind.SETTLEMENT_REPORT && request.kind() != ShareKind.PAYMENT_RESEND) {
            throw new ShareException(ShareErrorCode.UNSUPPORTED_KIND);
        }
        // sourceId가 없다면 예외를 반환한다. (현재는 무조건 sourceId가 존재. 추후 초대 링크에서는 없을 수도 있다)
        if (request.sourceId() == null) {
            throw new ShareException(ShareErrorCode.SOURCE_ID_REQUIRED);
        }
        return request.kind() == ShareKind.SETTLEMENT_REPORT
                ? shareReport(teacherId, request.sourceId(), request.participantId())
                : shareInvoiceReminder(teacherId, request.sourceId(), request.participantId());
    }

    // 정산 리포트: 코멘트를 확인한 리포트만 보낼 수 있다 (발송 확인과 같은 규칙). 이미 보낸 리포트도 다시 공유할 수 있다.
    private ShareResponse shareReport(Long teacherId, Long reportId, Long participantId) {
        SettlementReport report = settlementReportRepository.findWithTutoringById(reportId)
                .filter(found -> isOwnedBy(found.getTutoring(), teacherId))
                .orElseThrow(() -> new ReportException(ReportErrorCode.REPORT_NOT_FOUND));

        // 코멘트를 확인하지 않았다면 예외 발생
        if (!report.isCommentReviewed()) {
            throw new ReportException(ReportErrorCode.COMMENT_NOT_REVIEWED);
        }

        // 보낼 대상을 찾는다.
        Participant recipient = findRecipient(report.getTutoring(), participantId);

        // 예: "수업 8/8회 · 숙제 수행률 88% · 다음 8회차 교육비 360,000원". 숙제 결과 · 함께 보낸 청구가 없으면 그 부분은 뺀다.
        List<String> parts = new ArrayList<>();
        parts.add("수업 " + report.getCompletedCount() + "/" + report.getTargetCount() + "회");
        if (report.getHomeworkRate() != null) {
            parts.add("숙제 수행률 " + report.getHomeworkRate() + "%");
        }
        invoiceRepository.findActiveByReportIdAndTeacherId(reportId, teacherId)
                .ifPresent(invoice -> parts.add(invoice.displayName() + " 교육비 " + won(invoice.getAmount())));

        LinkShare share = request(recipient, ShareKind.SETTLEMENT_REPORT, ShareSourceType.REPORT, reportId);
        return new ShareResponse(share.getId(),
                shareLinkFactory.reportUrl(recipient.getLinkToken(), reportId),
                report.getTutoring().getStudentName() + " 학생 " + report.getTargetCount() + "회차 정산 리포트",
                String.join(SEPARATOR, parts),
                REPORT_BUTTON);
    }

    // 입금 안내 다시 보내기: 아직 다 받지 않은 청구만.
    private ShareResponse shareInvoiceReminder(Long teacherId, Long invoiceId, Long participantId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(found -> isOwnedBy(found.getTutoring(), teacherId))
                .orElseThrow(() -> new InvoiceException(InvoiceErrorCode.INVOICE_NOT_FOUND));
        if (invoice.getStatus() == InvoiceStatus.CANCELED) {
            throw new InvoiceException(InvoiceErrorCode.INVOICE_CANCELED);
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new InvoiceException(InvoiceErrorCode.ALREADY_PAID);
        }
        Participant recipient = findRecipient(invoice.getTutoring(), participantId);

        // 예: "다음 8회차 교육비 360,000원", 일부 입금이면 " · 남은 금액 240,000원"을 붙인다.
        String description = invoice.displayName() + " 교육비 " + won(invoice.getAmount());
        if (invoice.paidAmount() > 0) {
            description += SEPARATOR + "남은 금액 " + won(invoice.balance());
        }

        LinkShare share = request(recipient, ShareKind.PAYMENT_RESEND, ShareSourceType.INVOICE, invoiceId);
        return new ShareResponse(share.getId(),
                shareLinkFactory.invoiceUrl(recipient.getLinkToken(), invoiceId),
                invoice.getTutoring().getStudentName() + " 학생 교육비 안내",
                description,
                INVOICE_BUTTON);
    }

    // 받는 사람: 그 과외의 삭제되지 않은 학부모이고 링크 토큰이 있어야 한다. 남의 과외 참여자도 '없음'으로 응답한다.
    private Participant findRecipient(Tutoring tutoring, Long participantId) {
        Participant recipient = participantRepository.findById(participantId)
                .filter(found -> Objects.equals(found.getTutoring().getId(), tutoring.getId()))
                .filter(found -> found.getRemovedAt() == null)
                .filter(found -> found.getRole() == ParticipantRole.PARENT)
                .filter(found -> found.getLinkToken() != null)
                .orElseThrow(() -> new ShareException(ShareErrorCode.RECIPIENT_NOT_FOUND));
        // 받지 않기로 했거나 첫 입장에서 수신을 거부한 사람에게는 보내지 않는다 (초대 수락 대기는 보낼 수 있다).
        if (recipient.getReceiveMethod() == ReceiveMethod.NONE
                || recipient.getNotificationConsent() == NotificationConsent.REJECTED) {
            throw new ShareException(ShareErrorCode.RECIPIENT_REJECTED);
        }
        return recipient;
    }

    private LinkShare request(Participant recipient, ShareKind kind, ShareSourceType sourceType, Long sourceId) {
        linkShareRepository.dismissRequested(sourceType, sourceId, recipient.getId());
        return linkShareRepository.save(LinkShare.request(recipient, kind, sourceType, sourceId,
                LocalDateTime.now(clock)));
    }

    // 선생님 본인 과외이고 삭제되지 않은 과외인지 확인한다.
    private static boolean isOwnedBy(Tutoring tutoring, Long teacherId) {
        return Objects.equals(tutoring.getTeacher().getId(), teacherId) && !tutoring.isDeleted();
    }

    private static String won(int amount) {
        return String.format("%,d원", amount);
    }
}
