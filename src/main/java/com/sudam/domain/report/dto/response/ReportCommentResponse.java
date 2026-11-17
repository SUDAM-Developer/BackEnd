package com.sudam.domain.report.dto.response;

import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.enums.AiStatus;
import com.sudam.domain.report.enums.CommentSource;

import java.util.List;

/**
 * 정산 리포트 코멘트 응답 (조회 · 저장 공통).
 * aiStatus · softenedSentences는 노션 명세 모양만 맞춘 자리다. AI 기능 전이라 aiStatus는 출처가 AI_DRAFT일 때만 DONE이고,
 * softenedSentences는 항상 빈 목록이다.
 *
 * @param comment           코멘트. 아직 없으면 null
 * @param commentSource     'AI 초안' 배지. 코멘트가 없으면 null
 * @param reviewed          선생님이 확인했는지. false면 발송할 수 없다
 * @param aiStatus          AI 초안 생성 상태. AI 초안이 아니면 null
 * @param softenedSentences 부드럽게 고친 문장 (UT에서는 숨김)
 */
public record ReportCommentResponse(
        String comment,
        CommentSource commentSource,
        boolean reviewed,
        AiStatus aiStatus,
        List<SoftenedSentence> softenedSentences
) {

    public record SoftenedSentence(String original, String softened) {
    }

    public static ReportCommentResponse from(SettlementReport report) {
        AiStatus aiStatus = report.getCommentSource() == CommentSource.AI_DRAFT ? AiStatus.DONE : null;
        return new ReportCommentResponse(report.getComment(), report.getCommentSource(),
                report.isCommentReviewed(), aiStatus, List.of());
    }
}
