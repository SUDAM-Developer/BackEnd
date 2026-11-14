package com.sudam.domain.record.enums;

// 지난 숙제 수행 결과. 숙제 수행률 = (DONE + PARTIAL × 0.5) ÷ 값이 있는 기록 수.
public enum HomeworkResult {
    DONE, // 지난 숙제 모두 완료
    PARTIAL, // 지난 숙제 일부 완료
    NOT_DONE // 지난 숙제 안함
}
