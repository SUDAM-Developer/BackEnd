package com.sudam.domain.report.enums;

// 정산 리포트 발송 상태. 지금은 리포트를 만들기만 하므로 GENERATED만 쓴다.
public enum ReportStatus {
    GENERATED, // 생성됨 (발송 전)
    SCHEDULED, // 예약 발송 대기 (P1)
    SENT, // 발송 완료. 이후 내용 고정
    FAILED // 예약 발송 실패 (P1)
}
