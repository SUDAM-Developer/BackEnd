package com.sudam.domain.report.service;

import com.sudam.domain.report.dto.response.ReportCommentResponse;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.enums.AiStatus;
import com.sudam.domain.report.enums.CommentSource;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import com.sudam.global.exception.ProjectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportCommentService 코멘트 조회 · 확인")
class ReportCommentServiceTest {

    private static final Long TEACHER_ID = 1L;
    private static final Long OTHER_TEACHER_ID = 2L;
    private static final Long REPORT_ID = 21L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 12, 40);

    @Mock private SettlementReportRepository settlementReportRepository;

    private ReportCommentService reportCommentService;
    private SettlementReport report;

    @BeforeEach
    void setUp() {
        reportCommentService = new ReportCommentService(settlementReportRepository,
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
        TeacherProfile teacher = withId(TeacherProfile.create(
                User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app"),
                "한서윤", List.of(Subject.ENGLISH), null), TEACHER_ID);
        Tutoring tutoring = withId(Tutoring.builder().teacher(teacher).subjects(List.of(Subject.ENGLISH))
                .studentName("윤서아").gradeLevel(11).gradeSchoolYear(2026).build(), 10L);
        report = withId(SettlementReport.builder().tutoring(tutoring)
                .completedCount(8).targetCount(8).canceledCount(0)
                .understandingHigh(5).understandingMedium(3).understandingLow(0).build(), REPORT_ID);
    }

    @Test
    @DisplayName("코멘트가 아직 없으면 comment · commentSource · aiStatus가 null이고 확인 전이다")
    void 코멘트_없음_조회() {
        // given
        given(settlementReportRepository.findWithTutoringById(REPORT_ID)).willReturn(Optional.of(report));

        // when
        ReportCommentResponse response = reportCommentService.get(TEACHER_ID, REPORT_ID);

        // then
        assertThat(response.comment()).isNull();
        assertThat(response.commentSource()).isNull();
        assertThat(response.reviewed()).isFalse();
        assertThat(response.aiStatus()).isNull();
        assertThat(response.softenedSentences()).isEmpty();
    }

    @Test
    @DisplayName("AI 초안이 있으면 출처 AI_DRAFT · aiStatus DONE · 확인 전으로 조회된다")
    void AI_초안_조회() {
        // given
        report.draftComment("9월에는 장문 독해에서 큰 변화가 있었습니다.");
        given(settlementReportRepository.findWithTutoringById(REPORT_ID)).willReturn(Optional.of(report));

        // when
        ReportCommentResponse response = reportCommentService.get(TEACHER_ID, REPORT_ID);

        // then
        assertThat(response.comment()).isEqualTo("9월에는 장문 독해에서 큰 변화가 있었습니다.");
        assertThat(response.commentSource()).isEqualTo(CommentSource.AI_DRAFT);
        assertThat(response.reviewed()).isFalse();
        assertThat(response.aiStatus()).isEqualTo(AiStatus.DONE);
    }

    @Test
    @DisplayName("다른 선생님 과외의 리포트 코멘트를 조회하면 REPORT404_1 예외가 발생한다")
    void 남의_리포트_조회() {
        // given
        given(settlementReportRepository.findWithTutoringById(REPORT_ID)).willReturn(Optional.of(report));

        // when & then
        assertThatThrownBy(() -> reportCommentService.get(OTHER_TEACHER_ID, REPORT_ID))
                .isInstanceOf(ProjectException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo("REPORT404_1");
    }

    @Test
    @DisplayName("코멘트를 저장하면 현재 시각으로 확인되고 출처가 TEACHER로 응답된다")
    void 코멘트_저장() {
        // given
        report.draftComment("AI 초안");
        given(settlementReportRepository.findByIdForUpdate(REPORT_ID)).willReturn(Optional.of(report));

        // when
        ReportCommentResponse response = reportCommentService.save(TEACHER_ID, REPORT_ID, "선생님이 고친 코멘트");

        // then
        assertThat(report.getCommentReviewedAt()).isEqualTo(NOW);
        assertThat(response.comment()).isEqualTo("선생님이 고친 코멘트");
        assertThat(response.commentSource()).isEqualTo(CommentSource.TEACHER);
        assertThat(response.reviewed()).isTrue();
    }

    @Test
    @DisplayName("다른 선생님 과외의 리포트 코멘트를 저장하면 REPORT404_1 예외가 발생한다")
    void 남의_리포트_저장() {
        // given
        given(settlementReportRepository.findByIdForUpdate(REPORT_ID)).willReturn(Optional.of(report));

        // when & then
        assertThatThrownBy(() -> reportCommentService.save(OTHER_TEACHER_ID, REPORT_ID, "코멘트"))
                .isInstanceOf(ProjectException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo("REPORT404_1");
        assertThat(report.getComment()).isNull();
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
