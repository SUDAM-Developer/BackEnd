package com.sudam.domain.invoice.enums;

public enum InvoiceStatus {
    PENDING, // 입금 대기
    CONFIRM_REQUESTED, // 확인 대기 (학부모가 [입금했어요]를 누름)
    PARTIAL, // 일부 입금
    PAID, // 입금 완료
    CANCELED // 취소
}
