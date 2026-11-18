package com.sudam.domain.share.enums;

// 카카오톡 공유 종류 (ERD link_shares.kind). 지금은 SETTLEMENT_REPORT · PAYMENT_RESEND만 공유 정보를 만든다.
public enum ShareKind {
    INVITE, // 과외 초대
    INVITE_RESEND, // 과외 초대 다시
    LESSON_PLAN, // 수업 계획서
    FIRST_INVOICE, // 첫 교육비 안내
    LESSON_UPDATE, // 오늘 근황
    HOMEWORK, // 다음 숙제
    MAKEUP, // 보강 날짜 선택
    TIME_CHANGE, // 수업 시간 변경
    EVENT_SHARE, // 일정 공유
    SETTLEMENT_REPORT, // 정산 리포트 · 교육비 안내
    PAYMENT_RESEND, // 입금 안내 다시
    INVITE_ACCEPTED // 초대 수락
}
