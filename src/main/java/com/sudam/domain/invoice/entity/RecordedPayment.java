package com.sudam.domain.invoice.entity;

/**
 * Invoice.recordPayment의 결과.
 *
 * @param appliedAmount 실제로 반영한 금액. 요청 금액이 잔액보다 크면 잔액까지만 반영된다
 * @param payment       새로 만든 입금 행. 청구에 cascade(PERSIST)로 묶여 있어 따로 저장하지 않아도 flush 때 저장된다
 */
public record RecordedPayment(int appliedAmount, InvoicePayment payment) {
}
