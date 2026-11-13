package com.sudam.domain.participant.enums;

// 링크 첫 입장 시 수신 동의 (9.1.2).
public enum NotificationConsent {
    PENDING, // 초대 수락 대기
    AGREED, // 동의
    REJECTED // 거부
}
