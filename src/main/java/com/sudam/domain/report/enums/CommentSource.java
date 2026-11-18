package com.sudam.domain.report.enums;

/**
 * 정산 리포트 코멘트의 출처. 'AI 초안' 배지를 정한다.
 * 값은 ERD 그대로 둔다 (MySQL ENUM 컬럼이라 나중에 값을 더하면 스키마 변경이 필요하다).
 */
public enum CommentSource {
    AI_DRAFT, // AI 초안 그대로
    AI_EDITED, // AI 초안을 선생님이 고침 (AI 기능과 함께 사용, 원본 비교에 ai_draft_comment 필요)
    TEACHER // 선생님이 확인 · 작성
}
