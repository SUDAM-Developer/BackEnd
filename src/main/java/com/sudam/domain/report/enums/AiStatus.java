package com.sudam.domain.report.enums;

/**
 * AI 코멘트 초안 생성 상태. 값은 ERD의 ai_status 그대로 둔다.
 * AI 기능 전이라 컬럼은 없고, 코멘트 응답에서 출처가 AI_DRAFT면 DONE으로 계산한다.
 */
public enum AiStatus {
    PENDING,
    DONE,
    FAILED
}
