package com.sudam.domain.share.enums;

public enum ShareSentVia {
    WEBHOOK, // 카카오 공유 웹훅
    MANUAL, // 선생님 확인 (UT: 리포트 발송 확인 · 입금 안내 다시 보내기 확인 API)
    SYSTEM // 서버 기록 (일정 공유 · 초대 수락)
}
