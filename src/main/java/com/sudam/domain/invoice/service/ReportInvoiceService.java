package com.sudam.domain.invoice.service;

import com.sudam.domain.invoice.dto.request.AdjustInvoiceAmountRequest;
import com.sudam.domain.invoice.dto.response.ReportInvoiceResponse;
import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.enums.AdjustedBy;
import com.sudam.domain.invoice.exception.InvoiceException;
import com.sudam.domain.invoice.exception.code.InvoiceErrorCode;
import com.sudam.domain.invoice.repository.InvoiceRepository;
import com.sudam.domain.report.enums.ReportStatus;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.tutoring.entity.Tutoring;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 정산 리포트(T-14)의 교육비 안내 조회와 교육비 수정.
 * 두 API가 같은 응답 변환(ReportInvoiceResponse.of)을 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportInvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final SettlementReportRepository settlementReportRepository;

    /***
     * 함수 기능: 정산 리포트에 붙은 취소 안 된 청구의 교육비 안내를 만든다.
     *          리포트 엔티티를 거치지 않고 청구의 report_id로 바로 찾는다.
     * @param teacherId 로그인한 선생님 (users.id)
     * @param reportId  정산 리포트 ID
     * @return 교육비 안내 블록
     * @throws InvoiceException 청구가 없거나, 내 과외(삭제 안 됨)의 리포트가 아니면 INVOICE404_1
     */
    public ReportInvoiceResponse getReportInvoice(Long teacherId, Long reportId) {
        return invoiceRepository.findActiveByReportIdAndTeacherId(reportId, teacherId)
                .map(ReportInvoiceResponse::of)
                .orElseThrow(() -> new InvoiceException(InvoiceErrorCode.INVOICE_NOT_FOUND));
    }

    /***
     * 함수 기능: 선생님이 교육비 금액을 바꾼다 (6.1.3). 청구 행을 잠근 뒤 엔티티의 adjustAmount로 처리하고,
     *          바뀐 청구를 조회와 같은 교육비 안내 블록으로 돌려준다. 학부모 알림 · 리포트 다시 만들기는 하지 않는다.
     *          수정 이력 행은 Invoice.adjustments의 cascade(PERSIST)로 flush 때 저장되므로 따로 저장하지 않는다.
     * @param teacherId 로그인한 선생님 (users.id)
     * @param invoiceId 청구 ID
     * @param request   바꿀 금액 · 사유
     * @return 수정 후 교육비 안내 블록
     * @throws InvoiceException 청구가 없거나 내 과외의 청구가 아니면 INVOICE404_1, 함께 보낸 리포트가 발송 완료면 INVOICE409_4,
     *                          취소된 청구면 INVOICE409_1, 이미 받은 금액보다 적으면 INVOICE409_2
     */
    @Transactional
    public ReportInvoiceResponse adjustAmount(Long teacherId, Long invoiceId, AdjustInvoiceAmountRequest request) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .filter(found -> isOwnedBy(found.getTutoring(), teacherId))
                .orElseThrow(() -> new InvoiceException(InvoiceErrorCode.INVOICE_NOT_FOUND));

        validateReportNotSent(invoice);
        invoice.adjustAmount(request.amount(), request.reason(), AdjustedBy.TEACHER);

        return ReportInvoiceResponse.of(invoice);
    }

    // 발송한 리포트의 교육비는 학부모가 이미 본 금액이라 바꿀 수 없다. 리포트 엔티티가 이 도메인 밖이라 adjustAmount 대신 여기서 막는다.
    private void validateReportNotSent(Invoice invoice) {
        if (invoice.getReportId() == null) {
            return;
        }
        settlementReportRepository.findById(invoice.getReportId())
                .filter(report -> report.getStatus() == ReportStatus.SENT)
                .ifPresent(report -> {
                    throw new InvoiceException(InvoiceErrorCode.REPORT_ALREADY_SENT);
                });
    }

    private boolean isOwnedBy(Tutoring tutoring, Long teacherId) {
        return Objects.equals(tutoring.getTeacher().getId(), teacherId) && !tutoring.isDeleted();
    }
}
