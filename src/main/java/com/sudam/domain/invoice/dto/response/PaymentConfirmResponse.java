package com.sudam.domain.invoice.dto.response;

/**
 * 입금 확인 결과 (POST /invoices/{invoiceId}/payments).
 *
 * @param paymentId     새 입금 행 ID
 * @param appliedAmount 실제로 반영한 금액. 요청 금액이 잔액보다 크면 잔액까지만이다
 * @param item          입금을 반영한 뒤의 교육비 한 줄 (GET /invoices의 items[]와 같은 형식)
 */
public record PaymentConfirmResponse(Long paymentId, int appliedAmount, InvoiceItemResponse item) {
}
