package com.sudam.domain.invoice.enums;

public enum AdjustedBy {
    TEACHER, // 선생님이 직접 수정 (6.1.3)
    SYSTEM // 수업 종료 때 선불 미입금 청구를 진행 회차만큼으로 자동 변경 (7.1.2)
}
