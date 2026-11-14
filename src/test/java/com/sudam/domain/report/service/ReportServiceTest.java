package com.sudam.domain.report.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.report.dto.response.ReportResponse;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.entity.SettlementReportProgress;
import com.sudam.domain.report.enums.ReportStatus;
import com.sudam.domain.report.enums.ReportTextbookStatus;
import com.sudam.domain.report.exception.ReportException;
import com.sudam.domain.report.repository.SettlementReportProgressRepository;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.session.repository.SessionRepository;
import com.sudam.domain.tutoring.entity.Textbook;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.enums.ProgressUnit;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import com.sudam.global.exception.ProjectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService 정산 리포트 조회")
class ReportServiceTest {

    private static final Long TEACHER_ID = 1L;
    private static final Long OTHER_TEACHER_ID = 2L;
    private static final Long REPORT_ID = 21L;
    private static final Long CYCLE_ID = 88L;

    @Mock private SettlementReportRepository settlementReportRepository;
    @Mock private SettlementReportProgressRepository settlementReportProgressRepository;
    @Mock private SessionRepository sessionRepository;

    @InjectMocks
    private ReportService reportService;

    private SettlementReport report;
    private Textbook textbook;

    @BeforeEach
    void setUp() {
        TeacherProfile teacher = withId(TeacherProfile.create(
                User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app"),
                "한서윤", List.of(Subject.ENGLISH), null), TEACHER_ID);
        Tutoring tutoring = withId(Tutoring.builder().teacher(teacher).subject(Subject.ENGLISH)
                .studentName("윤서아").gradeLevel(11).gradeSchoolYear(2026).build(), 10L);
        Cycle cycle = withId(Cycle.open(tutoring, 1, LocalDateTime.of(2026, 9, 1, 0, 0)), CYCLE_ID);
        report = withId(SettlementReport.builder().tutoring(tutoring).cycle(cycle)
                .completedCount(8).targetCount(8).canceledCount(0).homeworkRate(88).prevHomeworkRate(75)
                .understandingHigh(5).understandingMedium(3).understandingLow(0).build(), REPORT_ID);
        textbook = withId(Textbook.builder().tutoring(tutoring).name("수능특강 영어")
                .progressUnit(ProgressUnit.CUSTOM).customUnitLabel("강").build(), 11L);
    }

    @Test
    @DisplayName("자기 과외의 리포트를 조회하면 요약 · 기간 · 진도를 돌려준다")
    void 리포트_조회_성공() {
        // given
        given(settlementReportRepository.findWithTutoringById(REPORT_ID)).willReturn(Optional.of(report));
        given(sessionRepository.findFirstRecognizedDate(CYCLE_ID)).willReturn(Optional.of(LocalDate.of(2026, 9, 14)));
        given(sessionRepository.findLastRecognizedDate(CYCLE_ID)).willReturn(Optional.of(LocalDate.of(2026, 9, 30)));
        given(settlementReportProgressRepository.findAllByReportIdOrderBySortOrder(REPORT_ID))
                .willReturn(List.of(SettlementReportProgress.create(report, textbook, "3강", "9강", 0)));

        // when
        ReportResponse response = reportService.get(TEACHER_ID, REPORT_ID);

        // then
        assertThat(response.reportId()).isEqualTo(REPORT_ID);
        assertThat(response.status()).isEqualTo(ReportStatus.GENERATED);
        assertThat(response.studentName()).isEqualTo("윤서아");
        assertThat(response.period())
                .isEqualTo(new ReportResponse.Period(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 30)));
        assertThat(response.stats()).isEqualTo(new ReportResponse.Stats(8, 8, 0, 88, 75,
                new ReportResponse.Understanding(5, 3, 0)));
        assertThat(response.progress()).containsExactly(
                new ReportResponse.Progress("수능특강 영어", "3강", "9강", ReportTextbookStatus.ACTIVE));
    }

    @Test
    @DisplayName("없는 리포트면 REPORT404_1")
    void 없는_리포트() {
        // given
        given(settlementReportRepository.findWithTutoringById(REPORT_ID)).willReturn(Optional.empty());

        // when & then
        assertReportError(TEACHER_ID, "REPORT404_1");
    }

    @Test
    @DisplayName("다른 선생님 과외의 리포트면 REPORT404_1")
    void 다른_선생님_리포트() {
        // given
        given(settlementReportRepository.findWithTutoringById(REPORT_ID)).willReturn(Optional.of(report));

        // when & then
        assertReportError(OTHER_TEACHER_ID, "REPORT404_1");
    }

    private void assertReportError(Long teacherId, String code) {
        assertThatThrownBy(() -> reportService.get(teacherId, REPORT_ID))
                .isInstanceOf(ReportException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo(code);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
