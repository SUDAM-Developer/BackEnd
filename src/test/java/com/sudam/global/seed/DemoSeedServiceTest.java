package com.sudam.global.seed;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.enums.CycleStatus;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.repository.InvoiceRepository;
import com.sudam.domain.participant.repository.ParticipantRepository;
import com.sudam.domain.participant.service.LinkTokenGenerator;
import com.sudam.domain.record.repository.SessionProgressRepository;
import com.sudam.domain.record.repository.SessionRecordRepository;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.entity.SettlementReportView;
import com.sudam.domain.report.enums.CommentSource;
import com.sudam.domain.report.enums.ReportStatus;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.report.repository.SettlementReportViewRepository;
import com.sudam.domain.session.entity.Session;
import com.sudam.domain.session.repository.SessionRepository;
import com.sudam.domain.settlement.port.SettlementReportCreator;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.repository.TextbookRepository;
import com.sudam.domain.tutoring.repository.TutoringPolicyRepository;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import com.sudam.domain.tutoring.repository.TutoringScheduleRepository;
import com.sudam.domain.tutoring.repository.TutoringSettingRepository;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.repository.TeacherProfileRepository;
import com.sudam.domain.user.repository.TermsRepository;
import com.sudam.domain.user.repository.UserRepository;
import com.sudam.domain.user.repository.UserTermsAgreementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DemoSeedServiceTest {

    // 2026-11-16(월) 10:00 실행 → 수입 카드 사이클 1은 10/12 시작, 11/09 10:00 정산.
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 10, 0);
    private static final LocalDate INCOME_CYCLE_START = LocalDate.of(2026, 10, 12);
    private static final LocalDate INCOME_SETTLED_DATE = LocalDate.of(2026, 11, 9);
    private static final long REPORT_ID_OFFSET = 1_000L;
    private static final List<String> STUDENTS = DemoIncomeCard.ALL.stream().map(DemoIncomeCard::studentName).toList();

    @Mock private UserRepository userRepository;
    @Mock private TeacherProfileRepository teacherProfileRepository;
    @Mock private TermsRepository termsRepository;
    @Mock private UserTermsAgreementRepository userTermsAgreementRepository;
    @Mock private TutoringRepository tutoringRepository;
    @Mock private TutoringScheduleRepository tutoringScheduleRepository;
    @Mock private TextbookRepository textbookRepository;
    @Mock private TutoringPolicyRepository tutoringPolicyRepository;
    @Mock private TutoringSettingRepository tutoringSettingRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private CycleRepository cycleRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private SessionRecordRepository sessionRecordRepository;
    @Mock private SessionProgressRepository sessionProgressRepository;
    @Mock private SettlementReportCreator settlementReportCreator;
    @Mock private SettlementReportRepository settlementReportRepository;
    @Mock private SettlementReportViewRepository settlementReportViewRepository;
    @Mock private DemoSeedResetter demoSeedResetter;
    @Mock private DemoTutoringBackdater demoTutoringBackdater;
    @Mock private Clock clock;
    @Spy private LinkTokenGenerator linkTokenGenerator = new LinkTokenGenerator();

    @InjectMocks
    private DemoSeedService demoSeedService;

    @Test
    @DisplayName("시드 선생님에게 과외가 이미 있으면 아무것도 만들지 않고 건너뛴다")
    void 이미_과외가_있으면_건너뜀() {
        // given
        User teacher = User.createTeacher(Provider.KAKAO, "seed-teacher-1", "seed-teacher@sudam.test");
        ReflectionTestUtils.setField(teacher, "id", 1L);
        given(userRepository.findByProviderAndProviderId(Provider.KAKAO, "seed-teacher-1"))
                .willReturn(Optional.of(teacher));
        given(tutoringRepository.existsByTeacherId(1L)).willReturn(true);

        // when
        DemoSeedResult result = demoSeedService.seed("seed-teacher-1", false);

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.teacherUserId()).isEqualTo(1L);
        verify(userRepository).findByProviderAndProviderId(Provider.KAKAO, "seed-teacher-1");
        verify(tutoringRepository).existsByTeacherId(1L);
        verifyNoMoreInteractions(userRepository, tutoringRepository);
        verifyNoInteractions(teacherProfileRepository, termsRepository, userTermsAgreementRepository,
                tutoringScheduleRepository, textbookRepository, tutoringPolicyRepository, tutoringSettingRepository,
                participantRepository, cycleRepository, invoiceRepository, sessionRepository,
                sessionRecordRepository, sessionProgressRepository, settlementReportCreator, settlementReportRepository,
                settlementReportViewRepository, demoSeedResetter, demoTutoringBackdater);
    }

    @Test
    @DisplayName("reset=true면 과외 존재 여부를 보기 전에 시드 선생님의 과외를 지운다 (같은 트랜잭션)")
    void 리셋이면_확인_전에_삭제() {
        // given
        User teacher = User.createTeacher(Provider.KAKAO, "seed-teacher-1", "seed-teacher@sudam.test");
        ReflectionTestUtils.setField(teacher, "id", 1L);
        given(userRepository.findByProviderAndProviderId(Provider.KAKAO, "seed-teacher-1"))
                .willReturn(Optional.of(teacher));
        // 순서만 확인하려고 삭제 뒤에도 과외가 남아 있다고 두어 생성 단계를 건너뛴다.
        given(tutoringRepository.existsByTeacherId(1L)).willReturn(true);

        // when
        demoSeedService.seed("seed-teacher-1", true);

        // then
        InOrder inOrder = inOrder(demoSeedResetter, tutoringRepository);
        inOrder.verify(demoSeedResetter).deleteTutoringsOf(1L);
        inOrder.verify(tutoringRepository).existsByTeacherId(1L);
    }

    @Test
    @DisplayName("수입 카드 4명 모두 정산한 사이클 1로 리포트 생성기를 부른다 (실제 정산은 사이클을 닫을 때 항상 리포트를 만든다)")
    void 리포트_생성기는_카드마다_사이클_1로_호출() {
        // given
        SeedCapture capture = seedAll();

        // when
        Map<String, Cycle> firstCycles = capture.firstCycles();

        // then
        assertThat(firstCycles).containsOnlyKeys(STUDENTS.toArray(String[]::new));
        verify(settlementReportCreator, times(STUDENTS.size())).create(anyLong(), anyLong());
        for (Cycle first : firstCycles.values()) {
            verify(settlementReportCreator).create(first.getTutoring().getId(), first.getId());
        }
    }

    @Test
    @DisplayName("리포트 생성기는 사이클 1이 SETTLED로 바뀐 뒤에 불린다 (실제 정산과 같은 순서)")
    void 리포트는_정산_뒤에_생성() {
        // given
        SeedCapture capture = seedAll();

        // when
        Map<Long, CycleStatus> statuses = capture.statusAtReportCreation();

        // then
        assertThat(statuses).hasSize(STUDENTS.size());
        assertThat(statuses.values()).containsOnly(CycleStatus.SETTLED);
    }

    @Test
    @DisplayName("사이클 2 청구의 reportId는 생성기가 그 과외의 사이클 1로 돌려준 ID다")
    void 청구에_리포트_ID_연결() {
        // given
        SeedCapture capture = seedAll();

        // when
        Map<String, Invoice> invoices = capture.invoices().stream()
                .collect(Collectors.toMap(invoice -> invoice.getTutoring().getStudentName(), invoice -> invoice));
        Map<String, Cycle> firstCycles = capture.firstCycles();

        // then
        assertThat(invoices).containsOnlyKeys(STUDENTS.toArray(String[]::new));
        for (String student : STUDENTS) {
            assertThat(invoices.get(student).getReportId())
                    .isEqualTo(firstCycles.get(student).getId() + REPORT_ID_OFFSET);
        }
    }

    @Test
    @DisplayName("수입 카드 리포트마다 그 학생의 AI 초안이 들어가고, 박지후만 확인 전 GENERATED로 남는다 (UT 시작 상태)")
    void 리포트에_AI_초안_코멘트() {
        // given
        SeedCapture capture = seedAll();

        // when
        Map<String, SettlementReport> reports = capture.reportsByStudent();

        // then
        for (DemoIncomeCard card : DemoIncomeCard.ALL) {
            SettlementReport report = reports.get(card.studentName());
            assertThat(report.getComment()).isEqualTo(card.aiDraftComment());
        }
        assertThat(reports.get("박지후").getCommentSource()).isEqualTo(CommentSource.AI_DRAFT);
        assertThat(reports.get("박지후").isCommentReviewed()).isFalse();
        assertThat(reports.get("박지후").getStatus()).isEqualTo(ReportStatus.GENERATED);
        assertThat(List.of("윤서아", "이도윤", "김하린")).allSatisfy(name ->
                assertThat(reports.get(name).getStatus()).isEqualTo(ReportStatus.SENT));
    }

    @Test
    @DisplayName("보내고 학부모가 연 리포트(윤서아 · 김하린)만 첫 열람을 저장한다")
    void 열람한_리포트만_열람_저장() {
        // given
        SeedCapture capture = seedAll();
        Map<String, SettlementReport> reports = capture.reportsByStudent();

        // when
        ArgumentCaptor<SettlementReportView> views = ArgumentCaptor.forClass(SettlementReportView.class);
        verify(settlementReportViewRepository, times(2)).save(views.capture());

        // then
        assertThat(views.getAllValues()).extracting(SettlementReportView::getReport)
                .containsExactly(reports.get("윤서아"), reports.get("김하린"));
    }

    @Test
    @DisplayName("수입 카드의 사이클 1 회차 수는 정산 회차와 같고, 날짜는 사이클 시작일 ~ 정산 전날의 수업 요일이다")
    void 사이클_1_회차_수는_정산_회차() {
        // given
        SeedCapture capture = seedAll();

        // when
        Map<String, List<Session>> firstCycleSessions = capture.sessions().stream()
                .filter(session -> session.getCycle().getCycleNo() == 1)
                .filter(session -> !session.getTutoring().getStudentName().equals(DemoLessonPlan.STUDENT_NAME))
                .collect(Collectors.groupingBy(session -> session.getTutoring().getStudentName()));

        // then
        assertThat(firstCycleSessions).containsOnlyKeys(STUDENTS.toArray(String[]::new));
        for (DemoIncomeCard card : DemoIncomeCard.ALL) {
            List<Session> sessions = firstCycleSessions.get(card.studentName());
            assertThat(sessions).hasSize(card.settlementSessionCount());
            assertThat(sessions).allSatisfy(session -> {
                assertThat(session.getOriginalDate()).isBetween(INCOME_CYCLE_START, INCOME_SETTLED_DATE.minusDays(1));
                assertThat(card.lessonDays()).contains(session.getOriginalDate().getDayOfWeek());
            });
        }
    }

    @Test
    @DisplayName("과외마다 생성 시각을 일정 시작일로 당긴다 (수입 카드는 사이클 시작일, 김서아는 첫 기록 날짜)")
    void 과외_생성_시각을_일정_시작일로_당긴다() {
        // given
        SeedCapture capture = seedAll();
        ArgumentCaptor<Tutoring> tutorings = ArgumentCaptor.forClass(Tutoring.class);
        ArgumentCaptor<LocalDateTime> createdAts = ArgumentCaptor.forClass(LocalDateTime.class);

        // when
        verify(demoTutoringBackdater, times(STUDENTS.size() + 1)).backdate(tutorings.capture(), createdAts.capture());

        // then
        Map<String, LocalDateTime> createdAtByStudent = new HashMap<>();
        for (int i = 0; i < tutorings.getAllValues().size(); i++) {
            createdAtByStudent.put(tutorings.getAllValues().get(i).getStudentName(), createdAts.getAllValues().get(i));
        }
        STUDENTS.forEach(student ->
                assertThat(createdAtByStudent).containsEntry(student, INCOME_CYCLE_START.atStartOfDay()));
        LocalDate demoFirstDate = capture.sessions().stream()
                .filter(session -> session.getTutoring().getStudentName().equals(DemoLessonPlan.STUDENT_NAME))
                .map(Session::getOriginalDate)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        assertThat(createdAtByStudent).containsEntry(DemoLessonPlan.STUDENT_NAME, demoFirstDate.atStartOfDay());
    }

    /***
     * 함수 기능: 저장소 목이 저장할 때 ID를 붙여 돌려주게 하고 전체 시드를 실행한 뒤, 저장된 사이클 · 회차 · 청구를 모은다.
     *          리포트 생성기 목은 "사이클 ID + 1000"을 리포트 ID로 돌려주고, 불린 시점의 사이클 상태를 기록한다.
     * @return 저장된 엔티티 모음
     */
    private SeedCapture seedAll() {
        User teacher = User.createTeacher(Provider.KAKAO, "seed-teacher-1", "seed-teacher@sudam.test");
        ReflectionTestUtils.setField(teacher, "id", 1L);
        given(userRepository.findByProviderAndProviderId(Provider.KAKAO, "seed-teacher-1"))
                .willReturn(Optional.of(teacher));
        given(clock.instant()).willReturn(NOW.atZone(ZONE).toInstant());
        given(clock.getZone()).willReturn(ZONE);

        AtomicLong ids = new AtomicLong(100);
        List<Cycle> cycles = new ArrayList<>();
        List<Session> sessions = new ArrayList<>();
        assignIdOnSave(teacherProfileRepository, ids, null);
        assignIdOnSave(tutoringRepository, ids, null);
        assignIdOnSave(tutoringScheduleRepository, ids, null);
        assignIdOnSave(textbookRepository, ids, null);
        assignIdOnSave(tutoringPolicyRepository, ids, null);
        assignIdOnSave(tutoringSettingRepository, ids, null);
        assignIdOnSave(participantRepository, ids, null);
        assignIdOnSave(cycleRepository, ids, cycles);
        assignIdOnSave(sessionRepository, ids, sessions);
        assignIdOnSave(sessionRecordRepository, ids, null);
        assignIdOnSave(sessionProgressRepository, ids, null);
        assignIdOnSave(invoiceRepository, ids, null);

        Map<Long, CycleStatus> statusAtReportCreation = new HashMap<>();
        given(settlementReportCreator.create(anyLong(), anyLong())).willAnswer(invocation -> {
            Long cycleId = invocation.getArgument(1);
            cycles.stream().filter(cycle -> cycle.getId().equals(cycleId)).findFirst()
                    .ifPresent(cycle -> statusAtReportCreation.put(cycleId, cycle.getStatus()));
            return cycleId + REPORT_ID_OFFSET;
        });
        // 생성기가 만든 리포트를 시드가 다시 조회해 초안을 넣으므로, ID마다 빈 리포트를 하나씩 돌려준다.
        Map<Long, SettlementReport> reports = new HashMap<>();
        given(settlementReportRepository.findById(anyLong())).willAnswer(invocation -> Optional.of(
                reports.computeIfAbsent(invocation.getArgument(0), id -> SettlementReport.builder()
                        .completedCount(8).targetCount(8).canceledCount(0)
                        .understandingHigh(0).understandingMedium(0).understandingLow(0).build())));

        DemoSeedResult result = demoSeedService.seed("seed-teacher-1", false);
        assertThat(result.created()).isTrue();

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, times(DemoIncomeCard.ALL.size())).save(invoiceCaptor.capture());
        return new SeedCapture(cycles, sessions, invoiceCaptor.getAllValues(), statusAtReportCreation, reports);
    }

    // 저장할 때 IDENTITY처럼 ID를 붙인다. ID 필드가 따로 없는 엔티티(@MapsId)는 그대로 둔다.
    private static <T> void assignIdOnSave(CrudRepository<T, ?> repository, AtomicLong ids, List<T> saved) {
        given(repository.save(any())).willAnswer(invocation -> {
            T entity = invocation.getArgument(0);
            if (ReflectionUtils.findField(entity.getClass(), "id") != null) {
                ReflectionTestUtils.setField(entity, "id", ids.incrementAndGet());
            }
            if (saved != null) {
                saved.add(entity);
            }
            return entity;
        });
    }

    private record SeedCapture(List<Cycle> cycles, List<Session> sessions, List<Invoice> invoices,
                               Map<Long, CycleStatus> statusAtReportCreation, Map<Long, SettlementReport> reports) {

        // 수입 카드 과외의 사이클 1 (학생 이름 → 사이클). 김서아는 뺀다.
        Map<String, Cycle> firstCycles() {
            return cycles.stream()
                    .filter(cycle -> cycle.getCycleNo() == 1)
                    .filter(cycle -> !cycle.getTutoring().getStudentName().equals(DemoLessonPlan.STUDENT_NAME))
                    .collect(Collectors.toMap(cycle -> cycle.getTutoring().getStudentName(), cycle -> cycle));
        }

        // 수입 카드 과외의 사이클 1 리포트 (학생 이름 → 리포트).
        Map<String, SettlementReport> reportsByStudent() {
            return firstCycles().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            entry -> reports.get(entry.getValue().getId() + REPORT_ID_OFFSET)));
        }
    }
}
