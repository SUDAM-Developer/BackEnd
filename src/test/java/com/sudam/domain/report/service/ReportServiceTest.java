package com.sudam.domain.report.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.record.enums.Understanding;
import com.sudam.domain.participant.dto.response.ParentResponse;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.participant.repository.ParticipantRepository;
import com.sudam.domain.report.dto.response.ReportDeliveryListResponse;
import com.sudam.domain.report.dto.response.ReportResponse;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.entity.SettlementReportProgress;
import com.sudam.domain.report.enums.ReportStatus;
import com.sudam.domain.report.enums.ReportTextbookStatus;
import com.sudam.domain.report.exception.ReportException;
import com.sudam.domain.report.repository.SettlementReportProgressRepository;
import com.sudam.domain.report.repository.ReportFirstViewedAt;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.report.repository.SettlementReportViewRepository;
import com.sudam.domain.session.repository.CycleFirstRecognizedDate;
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
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService 정산 리포트 조회")
class ReportServiceTest {

    private static final Long TEACHER_ID = 1L;
    private static final Long OTHER_TEACHER_ID = 2L;
    private static final Long REPORT_ID = 21L;
    private static final Long CYCLE_ID = 88L;
    private static final Long OTHER_CYCLE_ID = 89L;

    @Mock private SettlementReportRepository settlementReportRepository;
    @Mock private SettlementReportProgressRepository settlementReportProgressRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private SettlementReportViewRepository settlementReportViewRepository;
    @Mock private ParticipantRepository participantRepository;

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
        Participant mother = withId(Participant.createParent(report.getTutoring(), ParticipantLabel.MOTHER,
                "01000000001"), 31L);
        given(participantRepository.findShareableParents(List.of(10L))).willReturn(List.of(mother));

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
        // 상 5 · 중 3 · 하 0 → 가중평균 2.625
        assertThat(response.understandingLevel()).isEqualTo(Understanding.HIGH);
        // 공유 정보 생성(POST /shares)의 받는 사람
        assertThat(response.parents()).containsExactly(new ParentResponse(31L, ParticipantLabel.MOTHER));
    }

    @Test
    @DisplayName("리포트 목록은 레포지토리 순서대로 과외 · 학생 이름 · 정산 월 · 발송 상태를 담는다")
    void 리포트_목록_조회() {
        // given
        given(settlementReportRepository.findAllOfTeacher(TEACHER_ID)).willReturn(List.of(report));
        given(sessionRepository.findFirstRecognizedDates(List.of(CYCLE_ID)))
                .willReturn(List.of(new CycleFirstRecognizedDate(CYCLE_ID, LocalDate.of(2026, 9, 14))));

        // when
        ReportDeliveryListResponse response = reportService.listDeliveries(TEACHER_ID);

        // then
        assertThat(response.items()).containsExactly(new ReportDeliveryListResponse.Item(
                REPORT_ID, 10L, "윤서아", YearMonth.of(2026, 9), ReportStatus.GENERATED, null, null, null));
    }

    @Test
    @DisplayName("열람 기록이 있는 리포트만 viewedAt에 첫 열람 시각이 담기고, 없으면 null이다")
    void 리포트_목록_열람_시각() {
        // given
        SettlementReport unreadReport = reportOfCycleStartedAt(LocalDateTime.of(2026, 10, 1, 0, 0));
        given(settlementReportRepository.findAllOfTeacher(TEACHER_ID)).willReturn(List.of(unreadReport, report));
        given(settlementReportViewRepository.findFirstViewedAts(List.of(22L, REPORT_ID)))
                .willReturn(List.of(new ReportFirstViewedAt(REPORT_ID, LocalDateTime.of(2026, 10, 2, 20, 15))));

        // when
        ReportDeliveryListResponse response = reportService.listDeliveries(TEACHER_ID);

        // then
        assertThat(response.items()).extracting(ReportDeliveryListResponse.Item::reportId,
                        ReportDeliveryListResponse.Item::viewedAt)
                .containsExactly(tuple(22L, null), tuple(REPORT_ID, LocalDateTime.of(2026, 10, 2, 20, 15)));
    }

    @Test
    @DisplayName("사이클이 앞 달 말일에 열렸어도 정산 월은 첫 인정 회차의 월이다")
    void 정산_월_첫_인정_회차_기준() {
        // given
        SettlementReport monthEndReport = reportOfCycleStartedAt(LocalDateTime.of(2026, 8, 31, 23, 59));
        given(settlementReportRepository.findAllOfTeacher(TEACHER_ID)).willReturn(List.of(monthEndReport));
        given(sessionRepository.findFirstRecognizedDates(List.of(OTHER_CYCLE_ID)))
                .willReturn(List.of(new CycleFirstRecognizedDate(OTHER_CYCLE_ID, LocalDate.of(2026, 9, 1))));

        // when
        ReportDeliveryListResponse response = reportService.listDeliveries(TEACHER_ID);

        // then
        assertThat(response.items()).extracting(ReportDeliveryListResponse.Item::settlementMonth)
                .containsExactly(YearMonth.of(2026, 9));
    }

    @Test
    @DisplayName("인정 회차가 없는 사이클의 정산 월은 사이클 시작월이다")
    void 정산_월_인정_회차_없음() {
        // given
        SettlementReport noSessionReport = reportOfCycleStartedAt(LocalDateTime.of(2026, 8, 31, 23, 59));
        given(settlementReportRepository.findAllOfTeacher(TEACHER_ID)).willReturn(List.of(noSessionReport));
        given(sessionRepository.findFirstRecognizedDates(List.of(OTHER_CYCLE_ID))).willReturn(List.of());

        // when
        ReportDeliveryListResponse response = reportService.listDeliveries(TEACHER_ID);

        // then
        assertThat(response.items()).extracting(ReportDeliveryListResponse.Item::settlementMonth)
                .containsExactly(YearMonth.of(2026, 8));
    }

    @Test
    @DisplayName("리포트가 없으면 회차를 조회하지 않고 빈 목록을 돌려준다")
    void 리포트_목록_없음() {
        // given
        given(settlementReportRepository.findAllOfTeacher(TEACHER_ID)).willReturn(List.of());

        // when
        ReportDeliveryListResponse response = reportService.listDeliveries(TEACHER_ID);

        // then
        assertThat(response.items()).isEmpty();
        then(sessionRepository).should(never()).findFirstRecognizedDates(any());
        then(settlementReportViewRepository).should(never()).findFirstViewedAts(any());
    }

    // 같은 과외의 다른 사이클 리포트. 사이클 시작 시각만 바꿔 정산 월 경계를 본다.
    private SettlementReport reportOfCycleStartedAt(LocalDateTime startedAt) {
        Cycle cycle = withId(Cycle.open(report.getTutoring(), 2, startedAt), OTHER_CYCLE_ID);
        return withId(SettlementReport.builder().tutoring(report.getTutoring()).cycle(cycle)
                .completedCount(8).targetCount(8).canceledCount(0).homeworkRate(null).prevHomeworkRate(null)
                .understandingHigh(0).understandingMedium(0).understandingLow(0).build(), 22L);
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
