package com.sudam.domain.session.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;
import com.sudam.domain.session.repository.RecognizedSession;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.user.enums.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("CycleTimeline 회차 사이클 계산 · 예측")
class CycleTimelineTest {

    private static final Long TUTORING_ID = 1L;
    private static final Long SCHEDULE_ID = 31L;
    private static final Long SETTLED_CYCLE_ID = 11L;
    private static final Long IN_PROGRESS_CYCLE_ID = 12L;
    // 2026-10-26은 월요일이다. 수업은 월 · 수.
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    private Tutoring tutoring;
    private List<Cycle> cycles;
    private List<RecognizedSession> recognized;

    @BeforeEach
    void setUp() {
        tutoring = withId(Tutoring.builder().subjects(List.of(Subject.ENGLISH)).studentName("윤서아")
                .gradeLevel(10).gradeSchoolYear(2026).build(), TUTORING_ID);
        Cycle settled = withId(Cycle.open(tutoring, 1, LocalDateTime.of(2026, 10, 1, 9, 0)), SETTLED_CYCLE_ID);
        settled.settle(LocalDateTime.of(2026, 10, 14, 20, 0));
        Cycle inProgress = withId(Cycle.open(tutoring, 2, LocalDateTime.of(2026, 10, 14, 20, 0)),
                IN_PROGRESS_CYCLE_ID);
        cycles = List.of(settled, inProgress);
        // 사이클 1: 10/5 · 10/7 · 10/12 · 10/14 (4회 정산), 사이클 2: 10/19 · 10/21
        recognized = List.of(
                new RecognizedSession(TUTORING_ID, 101L, SETTLED_CYCLE_ID, date(10, 5)),
                new RecognizedSession(TUTORING_ID, 102L, SETTLED_CYCLE_ID, date(10, 7)),
                new RecognizedSession(TUTORING_ID, 103L, SETTLED_CYCLE_ID, date(10, 12)),
                new RecognizedSession(TUTORING_ID, 104L, SETTLED_CYCLE_ID, date(10, 14)),
                new RecognizedSession(TUTORING_ID, 105L, IN_PROGRESS_CYCLE_ID, date(10, 19)),
                new RecognizedSession(TUTORING_ID, 106L, IN_PROGRESS_CYCLE_ID, date(10, 21)));
    }

    @Test
    @DisplayName("진행 중 사이클의 번호 · 인정 수 · 정산 회차를 알려준다 (홈 '오늘 기록하면 정산 n/N회차')")
    void 진행_중_사이클() {
        // when
        CycleTimeline timeline = timeline(List.of(policy(1, 4)), List.of());

        // then
        assertThat(timeline.currentCycleNo()).isEqualTo(2);
        assertThat(timeline.recognizedCount()).isEqualTo(2);
        assertThat(timeline.settlementSessionCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("저장된 인정 회차는 소속 사이클 안에서 정렬 순서대로 번호를 매긴다")
    void 인정_회차_번호() {
        // given
        CycleTimeline timeline = timeline(List.of(policy(1, 4)), List.of());

        // when
        SlotCycle current = timeline.cycleOf(stored(106L, date(10, 21), SessionStatus.COMPLETED, true));
        SlotCycle settled = timeline.cycleOf(stored(102L, date(10, 7), SessionStatus.COMPLETED, true));

        // then
        assertThat(current).isEqualTo(new SlotCycle(2, 2, 2, 4, false, false));
        assertThat(settled).isEqualTo(new SlotCycle(1, 2, 4, 4, false, false));
    }

    @Test
    @DisplayName("미인정 휴강은 날짜로 소속 사이클을 정하고 번호를 매기지 않는다")
    void 미인정_휴강() {
        // given
        CycleTimeline timeline = timeline(List.of(policy(1, 4)), List.of());

        // when
        SlotCycle inSettled = timeline.cycleOf(stored(201L, date(10, 14), SessionStatus.CANCELED, false));
        SlotCycle inCurrent = timeline.cycleOf(stored(202L, date(10, 15), SessionStatus.CANCELED, false));

        // then: 사이클 1 = (-, 10/14], 사이클 2 = (10/14, -)
        assertThat(inSettled).isEqualTo(new SlotCycle(1, null, 4, 4, false, false));
        assertThat(inCurrent).isEqualTo(new SlotCycle(2, null, 2, 4, false, false));
    }

    @Test
    @DisplayName("오늘 이후 예정 회차는 남은 회차를 넘으면 다음 사이클 1회차부터 센다")
    void 다음_사이클_예측() {
        // given: 남은 회차 = 4 - 2 = 2
        List<LessonSlot> upcoming = planned(date(10, 26), date(10, 28), date(11, 2), date(11, 4), date(11, 9));
        CycleTimeline timeline = timeline(List.of(policy(1, 4)), upcoming);

        // when
        List<SlotCycle> result = upcoming.stream().map(timeline::cycleOf).toList();

        // then
        assertThat(result).containsExactly(
                new SlotCycle(2, 3, 2, 4, false, true),
                new SlotCycle(2, 4, 2, 4, false, true),
                new SlotCycle(3, 1, 0, 4, true, true),
                new SlotCycle(3, 2, 0, 4, false, true),
                new SlotCycle(3, 3, 0, 4, false, true));
    }

    @Test
    @DisplayName("다음 사이클의 정산 회차는 정책 이력으로 정하고, 여러 사이클을 건너뛰어도 센다")
    void 정책_이력과_여러_사이클() {
        // given: 사이클 3부터 정산 회차 2
        List<LessonSlot> upcoming = planned(date(10, 26), date(10, 28), date(11, 2), date(11, 4), date(11, 9));
        CycleTimeline timeline = timeline(List.of(policy(1, 4), policy(3, 2)), upcoming);

        // when
        List<SlotCycle> result = upcoming.stream().map(timeline::cycleOf).toList();

        // then
        assertThat(result).extracting(SlotCycle::cycleNo, SlotCycle::noInCycle, SlotCycle::settlementSessionCount,
                        SlotCycle::startsNewCycle)
                .containsExactly(
                        tuple(2, 3, 4, false),
                        tuple(2, 4, 4, false),
                        tuple(3, 1, 2, true),
                        tuple(3, 2, 2, false),
                        tuple(4, 1, 2, true));
    }

    @Test
    @DisplayName("휴강한 예정 회차는 세지 않고, 저장된 보강 예정은 순서대로 센다")
    void 휴강_건너뛰고_보강_포함() {
        // given: 10/26 휴강, 10/27 보강 예정
        LessonSlot canceled = new LessonSlot(TUTORING_ID, 301L, SCHEDULE_ID, date(10, 26), date(10, 26),
                LocalTime.of(18, 0), LocalTime.of(19, 30), SessionType.REGULAR, SessionStatus.CANCELED, false,
                null, false);
        LessonSlot makeup = new LessonSlot(TUTORING_ID, 302L, null, null, date(10, 27), LocalTime.of(15, 0),
                LocalTime.of(16, 30), SessionType.MAKEUP, SessionStatus.SCHEDULED, null, null, false);
        List<LessonSlot> upcoming = new ArrayList<>(List.of(canceled, makeup));
        upcoming.addAll(planned(date(10, 28)));
        CycleTimeline timeline = timeline(List.of(policy(1, 4)), upcoming);

        // when & then
        assertThat(timeline.cycleOf(canceled)).isEqualTo(new SlotCycle(2, null, 2, 4, false, false));
        assertThat(timeline.cycleOf(makeup)).isEqualTo(new SlotCycle(2, 3, 2, 4, false, true));
        assertThat(timeline.cycleOf(upcoming.get(2))).isEqualTo(new SlotCycle(2, 4, 2, 4, false, true));
    }

    @Test
    @DisplayName("지난 미기록 회차는 지금 기록하면 될 번호를 받고, 예측 순번은 차지하지 않는다")
    void 지난_미기록() {
        // given
        LessonSlot missed = planned(date(10, 23)).getFirst();
        LessonSlot today = planned(TODAY).getFirst();
        CycleTimeline timeline = timeline(List.of(policy(1, 4)), List.of(today));

        // when & then
        assertThat(timeline.cycleOf(missed)).isEqualTo(new SlotCycle(2, 3, 2, 4, false, true));
        assertThat(timeline.cycleOf(today)).isEqualTo(new SlotCycle(2, 3, 2, 4, false, true));
    }

    @Test
    @DisplayName("예측 범위 밖의 앞으로 회차를 물으면 호출 오류다")
    void 예측_범위_밖() {
        // given
        CycleTimeline timeline = timeline(List.of(policy(1, 4)), planned(date(10, 26)));
        LessonSlot farAway = planned(date(12, 28)).getFirst();

        // when & then
        assertThatThrownBy(() -> timeline.cycleOf(farAway)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("마지막 정산 사이클의 마지막 인정 날짜를 구한다. 정산한 사이클이 없으면 빈 값")
    void 마지막_정산_날짜() {
        // given
        Cycle onlyInProgress = withId(Cycle.open(tutoring, 1, LocalDateTime.of(2026, 10, 1, 9, 0)), 21L);

        // when & then
        assertThat(timeline(List.of(policy(1, 4)), List.of()).lastSettledDate()).contains(date(10, 14));
        assertThat(CycleTimeline.lastSettledDate(cycles, recognized)).contains(date(10, 14));
        assertThat(CycleTimeline.lastSettledDate(List.of(onlyInProgress), List.of())).isEmpty();
    }

    @Test
    @DisplayName("적용할 정책이 없으면 데이터 이상이다")
    void 정책_없음() {
        // when & then
        assertThatThrownBy(() -> timeline(List.of(), List.of()).settlementSessionCount())
                .isInstanceOf(IllegalStateException.class);
    }

    private CycleTimeline timeline(List<TutoringPolicy> policies, List<LessonSlot> upcoming) {
        return CycleTimeline.of(cycles, recognized, policies, upcoming, TODAY);
    }

    private TutoringPolicy policy(int effectiveCycleNo, int settlementSessionCount) {
        return TutoringPolicy.builder().tutoring(tutoring).effectiveCycleNo(effectiveCycleNo)
                .settlementSessionCnt(settlementSessionCount).paymentTiming(PaymentTiming.POSTPAID)
                .tuitionFee(300_000).bankName("국민").accountNumber("123-456-789012").accountHolder("한서윤")
                .build();
    }

    private static LessonSlot stored(Long sessionId, LocalDate date, SessionStatus status, boolean recognized) {
        return new LessonSlot(TUTORING_ID, sessionId, SCHEDULE_ID, date, date, LocalTime.of(18, 0),
                LocalTime.of(19, 30), SessionType.REGULAR, status, recognized, null, false);
    }

    private static List<LessonSlot> planned(LocalDate... dates) {
        return Arrays.stream(dates)
                .map(date -> new LessonSlot(TUTORING_ID, null, SCHEDULE_ID, date, date, LocalTime.of(18, 0),
                        LocalTime.of(19, 30), SessionType.REGULAR, SessionStatus.SCHEDULED, null, null, false))
                .toList();
    }

    private static LocalDate date(int month, int day) {
        return LocalDate.of(2026, month, day);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
