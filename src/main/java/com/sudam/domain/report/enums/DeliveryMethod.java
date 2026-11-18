package com.sudam.domain.report.enums;

/**
 * 정산 리포트 발송 방법. 값은 ERD 그대로 둔다 (MySQL ENUM 컬럼).
 */
public enum DeliveryMethod {
    KAKAO_SHARE, // 선생님이 카카오톡 공유 창으로 보냄
    MANUAL, // 직접 보냈다고 표시 (발송 기록 기능과 함께 사용)
    SCHEDULED_PUSH // 예약 발송 (P1)
}
