package com.sudam.domain.invoice.dto.response;

import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.entity.InvoiceSnapshot;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 정산 리포트(T-14)의 교육비 안내 블록. 교육비 안내 조회와 교육비 수정 응답이 같은 구조를 쓴다.
 * 키 이름 · 순서는 노션 API 명세와 같게 고정한다.
 *
 * @param invoiceId            청구 ID
 * @param paymentTiming        선불 / 후불 (청구 스냅샷 컬럼)
 * @param settlementSessionCnt 정산 회차 (청구 스냅샷 컬럼)
 * @param billedCycleStartDate 정산한 사이클의 첫 인정 회차 날짜 (스냅샷 cycleStartDate). 정산 없이 만든 청구(FIRST)는 null
 * @param unitPrice            회당 단가 (수업료 ÷ 정산 회차, 원 미만 버림)
 * @param canceledCount        스냅샷 휴강 내역 개수
 * @param deductionAmount      정산 회차 × 단가에서 자동 계산 금액이 모자란 만큼. 최소 0
 * @param calculatedAmount     자동 계산 금액. 교육비를 수정해도 바뀌지 않는다
 * @param amount               최종 금액 (교육비 수정 반영)
 */
public record ReportInvoiceResponse(
        @Schema(description = "청구 ID", example = "301")
        Long invoiceId,

        @Schema(description = "선불 PREPAID / 후불 POSTPAID", example = "PREPAID")
        PaymentTiming paymentTiming,

        @Schema(description = "정산 회차", example = "8")
        Integer settlementSessionCnt,

        @Schema(description = "정산한 사이클의 첫 인정 회차 날짜 (yyyy-MM-dd). 선불 첫 청구(FIRST)는 null",
                example = "2026-09-14", nullable = true)
        LocalDate billedCycleStartDate,

        @Schema(description = "회당 단가(원)", example = "45000")
        Integer unitPrice,

        @Schema(description = "휴강 수", example = "0")
        Integer canceledCount,

        @Schema(description = "차감 금액(원) = max(0, 정산 회차 × 단가 − 자동 계산 금액)", example = "0")
        Integer deductionAmount,

        @Schema(description = "자동 계산 금액(원)", example = "360000")
        Integer calculatedAmount,

        @Schema(description = "최종 금액(원). 교육비 수정 반영", example = "375000")
        Integer amount
) {

    /***
     * 함수 기능: 청구 한 건을 교육비 안내 블록으로 바꾼다. 조회 · 수정 API가 같은 변환을 쓴다.
     *          차감 금액은 단가 원 미만 버림 때문에 정산 회차 × 단가가 자동 계산 금액보다 작아질 수 있어 0 아래로 내리지 않는다.
     * @param invoice 청구 (스냅샷 포함)
     * @return ReportInvoiceResponse
     */
    public static ReportInvoiceResponse of(Invoice invoice) {
        InvoiceSnapshot snapshot = invoice.getSnapshot();
        int fullAmount = invoice.getSettlementSessionCnt() * invoice.getUnitPrice();
        return new ReportInvoiceResponse(
                invoice.getId(),
                invoice.getPaymentTiming(),
                invoice.getSettlementSessionCnt(),
                snapshot != null ? snapshot.cycleStartDate() : null,
                invoice.getUnitPrice(),
                snapshot != null ? snapshot.canceledLessons().size() : 0,
                Math.max(0, fullAmount - invoice.getCalculatedAmount()),
                invoice.getCalculatedAmount(),
                invoice.getAmount()
        );
    }
}
