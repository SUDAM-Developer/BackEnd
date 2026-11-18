package com.sudam.domain.report.entity;

import com.sudam.domain.record.enums.Understanding;
import com.sudam.domain.report.enums.CommentSource;
import com.sudam.domain.report.enums.DeliveryMethod;
import com.sudam.domain.report.enums.ReportStatus;
import com.sudam.global.exception.ProjectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SettlementReport 이해도 수준 · 코멘트 확인 · 발송 상태 변경")
class SettlementReportTest {

    private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 10, 1, 9, 12, 40);
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 1, 9, 20, 0);

    private SettlementReport report;

    @BeforeEach
    void setUp() {
        report = SettlementReport.builder()
                .completedCount(8).targetCount(8).canceledCount(0)
                .understandingHigh(5).understandingMedium(3).understandingLow(0).build();
    }

    @ParameterizedTest(name = "상 {0} · 중 {1} · 하 {2} → {3}")
    @CsvSource({
            "1, 1, 0, HIGH",     // 평균 2.5 (HIGH 경계)
            "5, 3, 0, HIGH",     // 2.625 (시드 김하린)
            "6, 2, 0, HIGH",     // 2.75 (시드 박지후)
            "4, 5, 0, MEDIUM",   // 2.44… (HIGH 바로 아래)
            "2, 5, 1, MEDIUM",   // 2.125 (시드 윤서아)
            "0, 1, 1, MEDIUM",   // 1.5 (MEDIUM 경계)
            "0, 1, 2, LOW",      // 1.33… (MEDIUM 바로 아래)
            "0, 1, 3, LOW",      // 1.25 (시드 이도윤)
            "0, 0, 4, LOW"       // 1.0
    })
    @DisplayName("이해도 가중평균이 2.5 이상이면 HIGH, 1.5 이상이면 MEDIUM, 그 아래는 LOW다")
    void 이해도_수준(int high, int medium, int low, Understanding expected) {
        // given
        SettlementReport counted = SettlementReport.builder()
                .completedCount(8).targetCount(8).canceledCount(0)
                .understandingHigh(high).understandingMedium(medium).understandingLow(low).build();

        // when & then
        assertThat(counted.understandingLevel()).contains(expected);
    }

    @Test
    @DisplayName("이해도 기록이 하나도 없으면 이해도 수준은 빈 값이다")
    void 이해도_기록_없음() {
        // given
        SettlementReport empty = SettlementReport.builder()
                .completedCount(8).targetCount(8).canceledCount(0)
                .understandingHigh(0).understandingMedium(0).understandingLow(0).build();

        // when & then
        assertThat(empty.understandingLevel()).isEmpty();
    }

    @Test
    @DisplayName("AI 초안을 넣으면 출처가 AI_DRAFT이고 확인 전 상태다")
    void AI_초안_저장() {
        // when
        report.draftComment("9월에는 장문 독해에서 큰 변화가 있었습니다.");

        // then
        assertThat(report.getComment()).isEqualTo("9월에는 장문 독해에서 큰 변화가 있었습니다.");
        assertThat(report.getCommentSource()).isEqualTo(CommentSource.AI_DRAFT);
        assertThat(report.isCommentReviewed()).isFalse();
    }

    @Test
    @DisplayName("선생님이 코멘트를 저장하면 출처가 TEACHER로 바뀌고 확인 시각이 남는다")
    void 코멘트_확인() {
        // given
        report.draftComment("AI 초안");

        // when
        report.reviewComment("선생님이 고친 코멘트", REVIEWED_AT);

        // then
        assertThat(report.getComment()).isEqualTo("선생님이 고친 코멘트");
        assertThat(report.getCommentSource()).isEqualTo(CommentSource.TEACHER);
        assertThat(report.getCommentReviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(report.isCommentReviewed()).isTrue();
    }

    @Test
    @DisplayName("발송한 리포트의 코멘트를 고치면 REPORT409_1 예외가 발생한다")
    void 발송_후_코멘트_수정_거절() {
        // given
        report.reviewComment("확인한 코멘트", REVIEWED_AT);
        report.markSent(SENT_AT);

        // when & then
        assertThatThrownBy(() -> report.reviewComment("다시 고친 코멘트", SENT_AT.plusMinutes(1)))
                .isInstanceOf(ProjectException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo("REPORT409_1");
        assertThat(report.getComment()).isEqualTo("확인한 코멘트");
    }

    @Test
    @DisplayName("코멘트를 확인하지 않은 리포트를 발송하면 REPORT409_2 예외가 발생한다")
    void 미확인_발송_거절() {
        // given
        report.draftComment("AI 초안");

        // when & then
        assertThatThrownBy(() -> report.markSent(SENT_AT))
                .isInstanceOf(ProjectException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo("REPORT409_2");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.GENERATED);
    }

    @Test
    @DisplayName("코멘트를 확인한 리포트를 발송하면 SENT가 되고 발송 시각 · 카카오 공유가 남는다")
    void 발송_성공() {
        // given
        report.reviewComment("확인한 코멘트", REVIEWED_AT);

        // when
        report.markSent(SENT_AT);

        // then
        assertThat(report.getStatus()).isEqualTo(ReportStatus.SENT);
        assertThat(report.getSentAt()).isEqualTo(SENT_AT);
        assertThat(report.getDeliveryMethod()).isEqualTo(DeliveryMethod.KAKAO_SHARE);
    }

    @Test
    @DisplayName("이미 발송한 리포트를 다시 발송하면 처음 발송 시각을 유지한다")
    void 재발송은_첫_시각_유지() {
        // given
        report.reviewComment("확인한 코멘트", REVIEWED_AT);
        report.markSent(SENT_AT);

        // when
        report.markSent(SENT_AT.plusHours(1));

        // then
        assertThat(report.getStatus()).isEqualTo(ReportStatus.SENT);
        assertThat(report.getSentAt()).isEqualTo(SENT_AT);
    }
}
