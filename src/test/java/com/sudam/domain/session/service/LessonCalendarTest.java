package com.sudam.domain.session.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.enums.CycleStatus;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.session.entity.Session;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;
import com.sudam.domain.session.repository.RecognizedSession;
import com.sudam.domain.session.repository.SessionRepository;
import com.sudam.domain.session.repository.TutoringLastSettledDate;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.tutoring.enums.TutoringStatus;
import com.sudam.domain.tutoring.repository.TutoringPolicyRepository;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import com.sudam.domain.tutoring.repository.TutoringScheduleRepository;
import com.sudam.domain.user.enums.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("LessonCalendar 예정 회차 계산기")
class LessonCalendarTest {

    private static final Long TEACHER_ID = 3L;
    private static final Long TUTORING_ID = 7L;
    private static final Long OTHER_TUTORING_ID = 8L;
    private static final Long SCHEDULE_ID = 31L;
    private static final Long CYCLE_ID = 12L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-11-02는 월요일이다.
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    @Mock private TutoringRepository tutoringRepository;
    @Mock private TutoringScheduleRepository tutoringScheduleRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private CycleRepository cycleRepository;
    @Mock private TutoringPolicyRepository tutoringPolicyRepository;

    private LessonCalendar calendar;
    private Tutoring tutoring;
    private TutoringSchedule monday;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(10, 0).atZone(SEOUL).toInstant(), SEOUL);
        calendar = new LessonCalendar(tutoringRepository, tutoringScheduleRepository, sessionRepository,
                cycleRepository, tutoringPolicyRepository, clock);
        tutoring = tutoring(TUTORING_ID, LocalDateTime.of(2026, 10, 1, 9, 0));
        monday = withId(TutoringSchedule.builder().tutoring(tutoring).dayOfWeek(1)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 30))
                .effectiveFrom(LocalDate.of(2026, 10, 1)).build(), SCHEDULE_ID);
    }

    @Test
    @DisplayName("반복 일정 회차는 저장 행이 없으면 일정 시각의 예정 회차로 찾는다")
    void 회차_찾기_예정() {
        // given
        given(tutoringScheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(monday));
        given(sessionRepository.findByScheduleIdAndOriginalDate(SCHEDULE_ID, TODAY)).willReturn(Optional.empty());

        // when
        Optional<LessonSlot> slot = calendar.findLesson(tutoring, LessonRef.ofSchedule(SCHEDULE_ID, TODAY));

        // then
        assertThat(slot).contains(new LessonSlot(TUTORING_ID, null, SCHEDULE_ID, TODAY, TODAY,
                LocalTime.of(18, 0), LocalTime.of(19, 30), SessionType.REGULAR, SessionStatus.SCHEDULED,
                null, null, false));
    }

    @Test
    @DisplayName("반복 일정 회차에 저장 행이 있으면 저장 행으로 찾고 비어 있는 시각은 일정에서 채운다")
    void 회차_찾기_저장_행() {
        // given
        Session recorded = withId(Session.completeRegular(tutoring, cycle(), monday, TODAY), 1001L);
        given(tutoringScheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(monday));
        given(sessionRepository.findByScheduleIdAndOriginalDate(SCHEDULE_ID, TODAY))
                .willReturn(Optional.of(recorded));

        // when
        Optional<LessonSlot> slot = calendar.findLesson(tutoring, LessonRef.ofSchedule(SCHEDULE_ID, TODAY));

        // then
        assertThat(slot).contains(new LessonSlot(TUTORING_ID, 1001L, SCHEDULE_ID, TODAY, TODAY,
                LocalTime.of(18, 0), LocalTime.of(19, 30), SessionType.REGULAR, SessionStatus.COMPLETED,
                true, CYCLE_ID, false));
    }

    @Test
    @DisplayName("다른 과외의 일정이면 회차를 찾지 않는다")
    void 회차_찾기_다른_과외_일정() {
        // given
        Tutoring other = tutoring(OTHER_TUTORING_ID, LocalDateTime.of(2026, 10, 1, 9, 0));

        given(tutoringScheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(monday));

        // when & then
        assertThat(calendar.findLesson(other, LessonRef.ofSchedule(SCHEDULE_ID, TODAY))).isEmpty();
    }

    @Test
    @DisplayName("일정 요일이 아니거나 과외 생성일 이전 날짜면 회차를 찾지 않는다")
    void 회차_찾기_수업_날짜_아님() {
        // given
        given(tutoringScheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(monday));

        // when & then: 11/3은 화요일, 9/28은 과외 생성(10/1) 전 월요일
        assertThat(calendar.findLesson(tutoring, LessonRef.ofSchedule(SCHEDULE_ID, TODAY.plusDays(1)))).isEmpty();
        assertThat(calendar.findLesson(tutoring, LessonRef.ofSchedule(SCHEDULE_ID, LocalDate.of(2026, 9, 28))))
                .isEmpty();
    }

    @Test
    @DisplayName("저장된 회차 ID로 찾을 때 다른 과외의 회차면 찾지 않는다")
    void 회차_찾기_저장_ID() {
        // given
        Session recorded = withId(Session.completeRegular(tutoring, cycle(), monday, TODAY), 1001L);
        given(sessionRepository.findById(1001L)).willReturn(Optional.of(recorded));
        Tutoring other = tutoring(OTHER_TUTORING_ID, LocalDateTime.of(2026, 10, 1, 9, 0));

        // when & then
        assertThat(calendar.findLesson(tutoring, LessonRef.ofSession(1001L)))
                .hasValueSatisfying(slot -> assertThat(slot.startTime()).isEqualTo(LocalTime.of(18, 0)));
        assertThat(calendar.findLesson(other, LessonRef.ofSession(1001L))).isEmpty();
    }

    @Test
    @DisplayName("기간 회차는 마지막 정산 날짜까지의 계산 회차를 빼고 저장 행으로 덮어쓴다")
    void 기간_회차() {
        // given: 10/26까지 정산, 11/2 기록
        LocalDate from = LocalDate.of(2026, 10, 26);
        LocalDate to = LocalDate.of(2026, 11, 9);
        Session recorded = withId(Session.completeRegular(tutoring, cycle(), monday, TODAY), 1001L);
        given(sessionRepository.findLastSettledDates(Set.of(TUTORING_ID), CycleStatus.SETTLED))
                .willReturn(List.of(new TutoringLastSettledDate(TUTORING_ID, from)));
        given(tutoringScheduleRepository.findEffectiveIn(Set.of(TUTORING_ID), from, to)).willReturn(List.of(monday));
        given(sessionRepository.findInPeriod(Set.of(TUTORING_ID), from, to)).willReturn(List.of(recorded));

        // when
        List<LessonSlot> slots = calendar.lessonsOf(List.of(tutoring), from, to);

        // then
        assertThat(slots).extracting(LessonSlot::date, LessonSlot::sessionId).containsExactly(
                tuple(TODAY, 1001L),
                tuple(LocalDate.of(2026, 11, 9), null));
    }

    @Test
    @DisplayName("과외가 없으면 조회 없이 빈 목록이다")
    void 기간_회차_과외_없음() {
        // when & then
        assertThat(calendar.lessonsOf(List.of(), TODAY, TODAY)).isEmpty();
        verifyNoInteractions(sessionRepository, tutoringScheduleRepository);
    }

    @Test
    @DisplayName("선생님 회차는 수업 중이고 삭제되지 않은 과외만 대상으로 한다")
    void 선생님_하루_회차() {
        // given
        given(tutoringRepository.findAllByTeacherIdAndStatusAndDeletedAtIsNullOrderById(TEACHER_ID,
                TutoringStatus.ACTIVE)).willReturn(List.of(tutoring));
        given(sessionRepository.findLastSettledDates(Set.of(TUTORING_ID), CycleStatus.SETTLED)).willReturn(List.of());
        given(tutoringScheduleRepository.findEffectiveIn(Set.of(TUTORING_ID), TODAY, TODAY))
                .willReturn(List.of(monday));
        given(sessionRepository.findInPeriod(Set.of(TUTORING_ID), TODAY, TODAY)).willReturn(List.of());

        // when
        List<LessonSlot> slots = calendar.findTeacherLessonsOn(TEACHER_ID, TODAY);

        // then
        assertThat(slots).singleElement().satisfies(slot -> {
            assertThat(slot.scheduleId()).isEqualTo(SCHEDULE_ID);
            assertThat(slot.overlaps(LocalTime.of(19, 0), LocalTime.of(20, 0))).isTrue();
        });
    }

    @Test
    @DisplayName("사이클 타임라인은 오늘부터 until까지의 예정 회차로 다음 사이클을 예측한다")
    void 타임라인() {
        // given: 사이클 1 진행 중, 인정 1회, 정산 회차 2 → 11/2가 2회차, 11/9는 새 사이클 1회차
        LocalDate until = LocalDate.of(2026, 11, 9);
        Cycle cycle = cycle();
        given(cycleRepository.findAllByTutoringIdIn(Set.of(TUTORING_ID))).willReturn(List.of(cycle));
        given(sessionRepository.findRecognizedSessions(Set.of(TUTORING_ID))).willReturn(
                List.of(new RecognizedSession(TUTORING_ID, 900L, CYCLE_ID, LocalDate.of(2026, 10, 26))));
        given(tutoringPolicyRepository.findAllByTutoringIdIn(Set.of(TUTORING_ID))).willReturn(List.of(policy(2)));
        given(tutoringScheduleRepository.findEffectiveIn(Set.of(TUTORING_ID), TODAY, until))
                .willReturn(List.of(monday));
        given(sessionRepository.findInPeriod(any(), any(), any())).willReturn(List.of());

        // when
        Map<Long, CycleTimeline> timelines = calendar.timelines(List.of(tutoring), until);

        // then
        CycleTimeline timeline = timelines.get(TUTORING_ID);
        assertThat(timeline.recognizedCount()).isEqualTo(1);
        assertThat(timeline.cycleOf(planned(TODAY))).isEqualTo(new SlotCycle(1, 2, 1, 2, false, true));
        assertThat(timeline.cycleOf(planned(until))).isEqualTo(new SlotCycle(2, 1, 0, 2, true, true));
    }

    private LessonSlot planned(LocalDate date) {
        return new LessonSlot(TUTORING_ID, null, SCHEDULE_ID, date, date, LocalTime.of(18, 0),
                LocalTime.of(19, 30), SessionType.REGULAR, SessionStatus.SCHEDULED, null, null, false);
    }

    private Cycle cycle() {
        return withId(Cycle.open(tutoring, 1, LocalDateTime.of(2026, 10, 1, 9, 0)), CYCLE_ID);
    }

    private TutoringPolicy policy(int settlementSessionCount) {
        return TutoringPolicy.builder().tutoring(tutoring).effectiveCycleNo(1)
                .settlementSessionCnt(settlementSessionCount).paymentTiming(PaymentTiming.POSTPAID)
                .tuitionFee(300_000).bankName("국민").accountNumber("123-456-789012").accountHolder("한서윤")
                .build();
    }

    private static Tutoring tutoring(Long id, LocalDateTime createdAt) {
        Tutoring tutoring = withId(Tutoring.builder().subject(Subject.ENGLISH).studentName("윤서아")
                .gradeLevel(10).gradeSchoolYear(2026).build(), id);
        ReflectionTestUtils.setField(tutoring, "createdAt", createdAt);
        return tutoring;
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
