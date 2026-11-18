package com.sudam.domain.share.enums;

public enum ShareStatus {
    REQUESTED, // 공유 창 열림 (발송 기록 목록에는 안 보임)
    SENT, // 보냄
    VIEWED, // 열람 (초대 수락)
    DISMISSED // 보내지 않기 (같은 원인 · 같은 받는 사람으로 새 공유를 만들면 이전 것)
}
