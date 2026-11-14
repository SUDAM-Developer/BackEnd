package com.sudam.domain.report.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.invoice.entity.CanceledLesson;
import com.sudam.domain.record.entity.SessionProgress;
import com.sudam.domain.record.entity.SessionRecord;
import com.sudam.domain.record.enums.HomeworkResult;
import com.sudam.domain.record.enums.Understanding;
import com.sudam.domain.record.repository.SessionProgressRepository;
import com.sudam.domain.record.repository.SessionRecordRepository;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.entity.SettlementReportProgress;
import com.sudam.domain.report.enums.ReportStatus;
import com.sudam.domain.report.enums.ReportTextbookStatus;
import com.sudam.domain.report.repository.SettlementReportProgressRepository;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.session.entity.Session;
import com.sudam.domain.settlement.port.CycleSessionReader;
import com.sudam.domain.tutoring.entity.Textbook;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.ProgressUnit;
import com.sudam.domain.tutoring.repository.TutoringPolicyRepository;
import com.sudam.domain.user.enums.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementReportFactory 정산 리포트 생성")
class SettlementReportFactoryTest {

    private static final Long TUTORING_ID = 10L;
    private static final Long CYCLE_ID = 88L;
    private static final Long PREV_CYCLE_ID = 87L;
    private static final Long REPORT_ID = 21L;

    @Mock private CycleRepository cycleRepository;
    @Mock private TutoringPolicyRepository tutoringPolicyRepository;
    @Mock private SessionRecordRepository sessionRecordRepository;
    @Mock private SessionProgressRepository sessionProgressRepository;
    @Mock private SettlementReportRepository settlementReportRepository;
    @Mock private SettlementReportProgressRepository settlementReportProgressRepository;
    @Mock private CycleSessionReader cycleSessionReader;

    @InjectMocks
    private SettlementReportFactory factory;

    private Tutoring tutoring;
    private Cycle cycle;
    private Textbook grammar;
    private Textbook reading;

    @BeforeEach
    void setUp() {
        tutoring = withId(Tutoring.builder().subject(Subject.ENGLISH).studentName("윤서아")
                .gradeLevel(11).gradeSchoolYear(2026).build(), TUTORING_ID);
        cycle = withId(Cycle.open(tutoring, 2, LocalDateTime.of(2026, 9, 1, 0, 0)), CYCLE_ID);
        grammar = withId(Textbook.builder().tutoring(tutoring).name("Grammar Zone 기본")
                .progressUnit(ProgressUnit.UNIT).build(), 12L);
        reading = withId(Textbook.builder().tutoring(tutoring).name("수능특강 영어")
                .progressUnit(ProgressUnit.CUSTOM).customUnitLabel("강").build(), 11L);
    }

    @Test
    @DisplayName("사이클 기록으로 통계를 계산하고 교재별 첫 진도 ~ 마지막 진도를 저장한다")
    void 리포트_생성() {
        // given
        givenCycleAndPolicy();
        Session first = session(LocalDate.of(2026, 9, 14));
        Session second = session(LocalDate.of(2026, 9, 21));
        given(sessionRecordRepository.findAllRecognizedInCycle(CYCLE_ID)).willReturn(List.of(
                SessionRecord.create(first, Understanding.HIGH, null, null, null),
                SessionRecord.create(second, Understanding.MEDIUM, HomeworkResult.PARTIAL, null, null)));
        given(cycleSessionReader.countRecognized(TUTORING_ID, CYCLE_ID)).willReturn(2);
        given(cycleSessionReader.canceledIn(TUTORING_ID, CYCLE_ID))
                .willReturn(List.of(new CanceledLesson(LocalDate.of(2026, 9, 17), null)));
        givenPrevReportRate(75);
        given(settlementReportRepository.save(any(SettlementReport.class)))
                .willAnswer(invocation -> withId(invocation.<SettlementReport>getArgument(0), REPORT_ID));
        // 회차 정렬 순서: 1회차(문법 12~14, 독해 3~5) → 2회차(문법 15~20, 독해 6~9)
        given(sessionProgressRepository.findAllRecognizedInCycle(CYCLE_ID)).willReturn(List.of(
                SessionProgress.create(first, grammar, 12, 14),
                SessionProgress.create(first, reading, 3, 5),
                SessionProgress.create(second, grammar, 15, 20),
                SessionProgress.create(second, reading, 6, 9)));

        // when
        Long reportId = factory.create(TUTORING_ID, CYCLE_ID);

        // then
        assertThat(reportId).isEqualTo(REPORT_ID);
        ArgumentCaptor<SettlementReport> reportCaptor = ArgumentCaptor.forClass(SettlementReport.class);
        verify(settlementReportRepository).save(reportCaptor.capture());
        SettlementReport report = reportCaptor.getValue();
        assertThat(report.getCycle()).isSameAs(cycle);
        assertThat(report.getTutoring()).isSameAs(tutoring);
        assertThat(report.getCompletedCount()).isEqualTo(2);
        assertThat(report.getTargetCount()).isEqualTo(8);
        assertThat(report.getCanceledCount()).isEqualTo(1);
        assertThat(report.getHomeworkRate()).isEqualTo(50);
        assertThat(report.getPrevHomeworkRate()).isEqualTo(75);
        assertThat(report.getUnderstandingHigh()).isEqualTo(1);
        assertThat(report.getUnderstandingMedium()).isEqualTo(1);
        assertThat(report.getUnderstandingLow()).isZero();
        assertThat(report.getStatus()).isEqualTo(ReportStatus.GENERATED);

        // 교재 ID 순 (독해 11 → 문법 12)
        assertThat(captureProgressRows())
                .extracting(SettlementReportProgress::getTextbookName, SettlementReportProgress::getStartLabel,
                        SettlementReportProgress::getEndLabel, SettlementReportProgress::getTextbookStatus,
                        SettlementReportProgress::getSortOrder)
                .containsExactly(
                        tuple("수능특강 영어", "3강", "9강", ReportTextbookStatus.ACTIVE, 0),
                        tuple("Grammar Zone 기본", "Unit 12", "Unit 20", ReportTextbookStatus.ACTIVE, 1));
    }

    @Test
    @DisplayName("직전 사이클 리포트가 없고 숙제 결과도 없으면 두 수행률 모두 null이다")
    void 수행률_없음() {
        // given
        givenCycleAndPolicy();
        given(sessionRecordRepository.findAllRecognizedInCycle(CYCLE_ID)).willReturn(List.of(
                SessionRecord.create(session(LocalDate.of(2026, 9, 14)), Understanding.LOW, null, null, null)));
        given(cycleSessionReader.countRecognized(TUTORING_ID, CYCLE_ID)).willReturn(1);
        given(cycleSessionReader.canceledIn(TUTORING_ID, CYCLE_ID)).willReturn(List.of());
        given(cycleRepository.findByTutoringIdAndCycleNo(TUTORING_ID, 1)).willReturn(Optional.empty());
        given(settlementReportRepository.save(any(SettlementReport.class)))
                .willAnswer(invocation -> withId(invocation.<SettlementReport>getArgument(0), REPORT_ID));
        given(sessionProgressRepository.findAllRecognizedInCycle(CYCLE_ID)).willReturn(List.of());

        // when
        factory.create(TUTORING_ID, CYCLE_ID);

        // then
        ArgumentCaptor<SettlementReport> reportCaptor = ArgumentCaptor.forClass(SettlementReport.class);
        verify(settlementReportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getHomeworkRate()).isNull();
        assertThat(reportCaptor.getValue().getPrevHomeworkRate()).isNull();
        assertThat(reportCaptor.getValue().getUnderstandingLow()).isEqualTo(1);
        assertThat(captureProgressRows()).isEmpty();
    }

    private void givenCycleAndPolicy() {
        given(cycleRepository.findById(CYCLE_ID)).willReturn(Optional.of(cycle));
        TutoringPolicy policy = mock(TutoringPolicy.class);
        given(policy.getEffectiveCycleNo()).willReturn(1);
        given(policy.getSettlementSessionCnt()).willReturn(8);
        given(tutoringPolicyRepository.findAllByTutoringId(TUTORING_ID)).willReturn(List.of(policy));
    }

    private void givenPrevReportRate(int rate) {
        Cycle prev = withId(Cycle.open(tutoring, 1, LocalDateTime.of(2026, 8, 1, 0, 0)), PREV_CYCLE_ID);
        given(cycleRepository.findByTutoringIdAndCycleNo(TUTORING_ID, 1)).willReturn(Optional.of(prev));
        SettlementReport prevReport = SettlementReport.builder().tutoring(tutoring).cycle(prev)
                .homeworkRate(rate).build();
        given(settlementReportRepository.findByCycleId(PREV_CYCLE_ID)).willReturn(Optional.of(prevReport));
    }

    @SuppressWarnings("unchecked")
    private List<SettlementReportProgress> captureProgressRows() {
        ArgumentCaptor<List<SettlementReportProgress>> captor = ArgumentCaptor.forClass(List.class);
        verify(settlementReportProgressRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private Session session(LocalDate date) {
        return Session.completeRegular(tutoring, cycle, null, date);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
