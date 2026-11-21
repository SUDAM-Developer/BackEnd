package com.sudam.domain.session.service;

import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("LessonExpander 예정 회차 펼치기")
class LessonExpanderTest {

    private static final Long TUTORING_ID = 1L;
    private static final Long OTHER_TUTORING_ID = 2L;
    private static final Long SCHEDULE_ID = 31L;
    private static final LocalTime START = LocalTime.of(18, 0);
    private static final LocalTime END = LocalTime.of(19, 30);
    // 2026-11-02는 월요일이다.
    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_30 = LocalDate.of(2026, 11, 30);

    @Test
    @DisplayName("반복 일정을 기간 안에서 요일로 펼친다")
    void 요일_펼침() {
        // given
        ScheduleRule monday = rule(SCHEDULE_ID, TUTORING_ID, DayOfWeek.MONDAY, NOV_1, null);

        // when
        List<LessonSlot> slots = LessonExpander.expand(List.of(span(NOV_1, null, null)), List.of(monday),
                List.of(), NOV_1, NOV_30);

        // then
        assertThat(slots).extracting(LessonSlot::date).containsExactly(
                date(2), date(9), date(16), date(23), date(30));
        assertThat(slots.getFirst()).isEqualTo(new LessonSlot(TUTORING_ID, null, SCHEDULE_ID, date(2), date(2),
                START, END, SessionType.REGULAR, SessionStatus.SCHEDULED, null, null, false));
    }

    @Test
    @DisplayName("적용 시작일과 적용 종료일은 둘 다 포함한다")
    void 적용_기간_경계() {
        // given
        ScheduleRule monday = rule(SCHEDULE_ID, TUTORING_ID, DayOfWeek.MONDAY, date(9), date(23));

        // when
        List<LessonSlot> slots = LessonExpander.expand(List.of(span(NOV_1, null, null)), List.of(monday),
                List.of(), NOV_1, NOV_30);

        // then
        assertThat(slots).extracting(LessonSlot::date).containsExactly(date(9), date(16), date(23));
    }

    @Test
    @DisplayName("과외 생성일 이전 날짜는 만들지 않는다")
    void 과외_생성일_이전_제외() {
        // given
        ScheduleRule monday = rule(SCHEDULE_ID, TUTORING_ID, DayOfWeek.MONDAY, NOV_1, null);

        // when
        List<LessonSlot> slots = LessonExpander.expand(List.of(span(date(10), null, null)), List.of(monday),
                List.of(), NOV_1, NOV_30);

        // then
        assertThat(slots).extracting(LessonSlot::date).containsExactly(date(16), date(23), date(30));
    }

    @Test
    @DisplayName("종료한 과외는 종료일 다음 날부터 만들지 않는다")
    void 종료_과외() {
        // given
        ScheduleRule monday = rule(SCHEDULE_ID, TUTORING_ID, DayOfWeek.MONDAY, NOV_1, null);

        // when
        List<LessonSlot> slots = LessonExpander.expand(List.of(span(NOV_1, date(16), null)), List.of(monday),
                List.of(), NOV_1, NOV_30);

        // then
        assertThat(slots).extracting(LessonSlot::date).containsExactly(date(2), date(9), date(16));
    }

    @Test
    @DisplayName("마지막 정산 사이클의 마지막 인정 날짜까지는 계산 회차를 만들지 않는다")
    void 정산된_날짜_제외() {
        // given
        ScheduleRule monday = rule(SCHEDULE_ID, TUTORING_ID, DayOfWeek.MONDAY, NOV_1, null);

        // when
        List<LessonSlot> slots = LessonExpander.expand(List.of(span(NOV_1, null, date(9))), List.of(monday),
                List.of(), NOV_1, NOV_30);

        // then
        assertThat(slots).extracting(LessonSlot::date).containsExactly(date(16), date(23), date(30));
    }

    @Test
    @DisplayName("같은 일정 · 같은 원래 날짜의 저장 행이 있으면 그 행으로 덮어쓰고 비어 있는 시각은 일정에서 채운다")
    void 저장_행_덮어쓰기() {
        // given
        ScheduleRule monday = rule(SCHEDULE_ID, TUTORING_ID, DayOfWeek.MONDAY, NOV_1, null);
        LessonSlot completed = storedRegular(1001L, date(9), date(9), SessionStatus.COMPLETED, true, null, null);

        // when
        List<LessonSlot> slots = LessonExpander.expand(List.of(span(NOV_1, null, null)), List.of(monday),
                List.of(completed), date(2), date(16));

        // then
        assertThat(slots).hasSize(3);
        assertThat(slots.get(1)).isEqualTo(new LessonSlot(TUTORING_ID, 1001L, SCHEDULE_ID, date(9), date(9),
                START, END, SessionType.REGULAR, SessionStatus.COMPLETED, true, 77L, false));
    }

    @Test
    @DisplayName("시간을 바꾼 저장 행은 바뀐 시각을 그대로 쓴다")
    void 시간을_바꾼_저장_행() {
        // given
        ScheduleRule monday = rule(SCHEDULE_ID, TUTORING_ID, DayOfWeek.MONDAY, NOV_1, null);
        LessonSlot changed = storedRegular(1002L, date(9), date(9), SessionStatus.SCHEDULED, null,
                LocalTime.of(20, 0), LocalTime.of(21, 30));

        // when
        List<LessonSlot> slots = LessonExpander.expand(List.of(span(NOV_1, null, null)), List.of(monday),
                List.of(changed), date(9), date(9));

        // then
        assertThat(slots).singleElement().satisfies(slot -> {
            assertThat(slot.sessionId()).isEqualTo(1002L);
            assertThat(slot.startTime()).isEqualTo(LocalTime.of(20, 0));
            assertThat(slot.endTime()).isEqualTo(LocalTime.of(21, 30));
        });
    }

    @Test
    @DisplayName("다른 날짜에 기록한 회차는 원래 날짜의 예정 회차를 지우고 기록 날짜에 나온다")
    void 날짜를_옮긴_기록() {
        // given: 11/16(월) 수업을 11/18에 기록 (4.1.1 [그 수업으로])
        ScheduleRule monday = rule(SCHEDULE_ID, TUTORING_ID, DayOfWeek.MONDAY, NOV_1, null);
        LessonSlot moved = storedRegular(1003L, date(18), date(16), SessionStatus.COMPLETED, true, null, null);

        // when
        List<LessonSlot> week = LessonExpander.expand(List.of(span(NOV_1, null, null)), List.of(monday),
                List.of(moved), date(16), date(22));
        List<LessonSlot> originalDayOnly = LessonExpander.expand(List.of(span(NOV_1, null, null)), List.of(monday),
                List.of(moved), date(16), date(17));

        // then
        assertThat(week).singleElement().satisfies(slot -> {
            assertThat(slot.sessionId()).isEqualTo(1003L);
            assertThat(slot.date()).isEqualTo(date(18));
            assertThat(slot.originalDate()).isEqualTo(date(16));
        });
        assertThat(originalDayOnly).isEmpty();
    }

    @Test
    @DisplayName("반복 일정에 없는 저장 행(보강 · 추가)은 날짜가 기간 안일 때만 더한다")
    void 보강_추가() {
        // given
        LessonSlot makeup = new LessonSlot(TUTORING_ID, 1004L, null, null, date(11), LocalTime.of(15, 0),
                LocalTime.of(16, 30), SessionType.MAKEUP, SessionStatus.SCHEDULED, null, null, false);

        // when
        List<LessonSlot> inRange = LessonExpander.expand(List.of(span(NOV_1, null, null)), List.of(),
                List.of(makeup), date(10), date(12));
        List<LessonSlot> outOfRange = LessonExpander.expand(List.of(span(NOV_1, null, null)), List.of(),
                List.of(makeup), date(12), date(14));

        // then
        assertThat(inRange).containsExactly(makeup);
        assertThat(outOfRange).isEmpty();
    }

    @Test
    @DisplayName("같은 날 회차는 시작 시각 → 시각 없는 회차 순이고, 같은 시각이면 저장된 회차 id 순이다")
    void 같은_날_정렬() {
        // given
        ScheduleRule late = new ScheduleRule(41L, OTHER_TUTORING_ID, DayOfWeek.MONDAY, LocalTime.of(20, 0),
                LocalTime.of(21, 0), NOV_1, null);
        ScheduleRule early = rule(SCHEDULE_ID, TUTORING_ID, DayOfWeek.MONDAY, NOV_1, null);
        LessonSlot noTime = new LessonSlot(TUTORING_ID, 1010L, null, null, date(2), null, null,
                SessionType.EXTRA, SessionStatus.COMPLETED, true, 77L, false);
        LessonSlot sameTimeLaterId = new LessonSlot(OTHER_TUTORING_ID, 1012L, null, null, date(2), START, END,
                SessionType.MAKEUP, SessionStatus.SCHEDULED, null, null, false);
        LessonSlot sameTimeEarlierId = new LessonSlot(OTHER_TUTORING_ID, 1011L, null, null, date(2), START, END,
                SessionType.MAKEUP, SessionStatus.SCHEDULED, null, null, false);

        // when
        List<LessonSlot> slots = LessonExpander.expand(
                List.of(span(NOV_1, null, null), new TutoringSpan(OTHER_TUTORING_ID, NOV_1, null, null)),
                List.of(late, early), List.of(noTime, sameTimeLaterId, sameTimeEarlierId), date(2), date(2));

        // then: 18:00 저장(1011) → 18:00 저장(1012) → 18:00 계산 → 20:00 계산 → 시각 없음
        assertThat(slots).extracting(LessonSlot::sessionId, LessonSlot::scheduleId).containsExactly(
                tuple(1011L, null),
                tuple(1012L, null),
                tuple(null, SCHEDULE_ID),
                tuple(null, 41L),
                tuple(1010L, null));
    }

    @Test
    @DisplayName("일정 요일 · 적용 기간 · 과외 기간이 모두 맞아야 수업 날짜다")
    void 수업_날짜_판정() {
        // given
        ScheduleRule monday = rule(SCHEDULE_ID, TUTORING_ID, DayOfWeek.MONDAY, date(9), date(23));
        TutoringSpan span = span(NOV_1, date(16), null);

        // when & then
        assertThat(LessonExpander.occurs(span, monday, date(9))).isTrue();
        assertThat(LessonExpander.occurs(span, monday, date(10))).isFalse();  // 화요일
        assertThat(LessonExpander.occurs(span, monday, date(2))).isFalse();   // 적용 시작 전
        assertThat(LessonExpander.occurs(span, monday, date(23))).isFalse();  // 과외 종료 뒤
    }

    @Test
    @DisplayName("겹침 검사는 끝과 시작이 맞닿으면 겹치지 않는다")
    void 겹침_검사() {
        // given
        LessonSlot slot = new LessonSlot(TUTORING_ID, null, SCHEDULE_ID, date(2), date(2), START, END,
                SessionType.REGULAR, SessionStatus.SCHEDULED, null, null, false);

        // when & then
        assertThat(slot.overlaps(LocalTime.of(19, 30), LocalTime.of(21, 0))).isFalse();
        assertThat(slot.overlaps(LocalTime.of(16, 30), LocalTime.of(18, 0))).isFalse();
        assertThat(slot.overlaps(LocalTime.of(19, 0), LocalTime.of(20, 30))).isTrue();
        assertThat(slot.overlaps(LocalTime.of(17, 0), LocalTime.of(21, 0))).isTrue();
    }

    private static TutoringSpan span(LocalDate startDate, LocalDate endDate, LocalDate lockedUntil) {
        return new TutoringSpan(TUTORING_ID, startDate, endDate, lockedUntil);
    }

    private static ScheduleRule rule(Long scheduleId, Long tutoringId, DayOfWeek dayOfWeek, LocalDate from,
                                     LocalDate to) {
        return new ScheduleRule(scheduleId, tutoringId, dayOfWeek, START, END, from, to);
    }

    private static LessonSlot storedRegular(Long sessionId, LocalDate date, LocalDate originalDate,
                                            SessionStatus status, Boolean recognized, LocalTime start,
                                            LocalTime end) {
        Long cycleId = Boolean.TRUE.equals(recognized) ? 77L : null;
        return new LessonSlot(TUTORING_ID, sessionId, SCHEDULE_ID, originalDate, date, start, end,
                SessionType.REGULAR, status, recognized, cycleId, false);
    }

    private static LocalDate date(int dayOfNovember) {
        return LocalDate.of(2026, 11, dayOfNovember);
    }
}
