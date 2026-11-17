package com.sudam.domain.report.service;

import com.sudam.domain.report.dto.response.ReportSendResponse;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.enums.ReportStatus;
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
@DisplayName("ReportSendService 정산 리포트 발송 확인")
class ReportSendServiceTest {

    private static final Long TEACHER_ID = 1L;
    private static final Long OTHER_TEACHER_ID = 2L;
    private static final Long REPORT_ID = 21L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 20, 0);

    @Mock private SettlementReportRepository settlementReportRepository;

    private ReportSendService reportSendService;
    private SettlementReport report;

    @BeforeEach
    void setUp() {
        reportSendService = new ReportSendService(settlementReportRepository,
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
        TeacherProfile teacher = withId(TeacherProfile.create(
                User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app"),
                "한서윤", List.of(Subject.ENGLISH), null), TEACHER_ID);
        Tutoring tutoring = withId(Tutoring.builder().teacher(teacher).subject(Subject.ENGLISH)
                .studentName("윤서아").gradeLevel(11).gradeSchoolYear(2026).build(), 10L);
        report = withId(SettlementReport.builder().tutoring(tutoring)
                .completedCount(8).targetCount(8).canceledCount(0)
                .understandingHigh(5).understandingMedium(3).understandingLow(0).build(), REPORT_ID);
    }

    @Test
    @DisplayName("코멘트를 확인한 리포트를 발송하면 현재 시각으로 SENT가 된다")
    void 발송_성공() {
        // given
        report.reviewComment("확인한 코멘트", NOW.minusMinutes(5));
        given(settlementReportRepository.findByIdForUpdate(REPORT_ID)).willReturn(Optional.of(report));

        // when
        ReportSendResponse response = reportSendService.send(TEACHER_ID, REPORT_ID);

        // then
        assertThat(response.reportId()).isEqualTo(REPORT_ID);
        assertThat(response.status()).isEqualTo(ReportStatus.SENT);
        assertThat(response.sentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("다른 선생님 과외의 리포트를 발송하면 REPORT404_1 예외가 발생한다")
    void 남의_리포트_발송() {
        // given
        report.reviewComment("확인한 코멘트", NOW.minusMinutes(5));
        given(settlementReportRepository.findByIdForUpdate(REPORT_ID)).willReturn(Optional.of(report));

        // when & then
        assertThatThrownBy(() -> reportSendService.send(OTHER_TEACHER_ID, REPORT_ID))
                .isInstanceOf(ProjectException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo("REPORT404_1");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.GENERATED);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
