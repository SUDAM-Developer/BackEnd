package com.sudam.domain.invoice.enums;

// 수입 탭 이름 (6.1.2).
public enum InvoiceKind {
    FIRST, // 첫 N회차 (선불 첫 청구)
    NEXT, // 다음 N회차 (선불)
    CURRENT, // 이번 N회차 (후불)
    FINAL // 마지막 정산 (수업 종료)
}
