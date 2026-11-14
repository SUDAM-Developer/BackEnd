package com.sudam.global.seed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

class DemoLessonPlanTest {

    // 2026-11-09 = 월요일. 그 주 월~일을 하루씩 더해 만든다.
    private static final LocalDate MONDAY_OF_WEEK = LocalDate.of(2026, 11, 9);

    @ParameterizedTest(name = "{0}이면 월 · 수 · 금")
    @CsvSource({"MONDAY", "WEDNESDAY", "FRIDAY"})
    @DisplayName("오늘이 월 · 수 · 금이면 수업 요일은 월 · 수 · 금이다")
    void 월수금이면_월수금(DayOfWeek todayOfWeek) {
        // given
        LocalDate today = MONDAY_OF_WEEK.plusDays(todayOfWeek.getValue() - 1L);

        // when
        List<DayOfWeek> days = DemoLessonPlan.lessonDays(today);

        // then
        assertThat(days).containsExactly(MONDAY, WEDNESDAY, FRIDAY);
    }

    @ParameterizedTest(name = "{0}이면 {1}")
    @CsvSource({
            "TUESDAY, TUESDAY;THURSDAY;SATURDAY",
            "SATURDAY, MONDAY;WEDNESDAY;SATURDAY",
            "SUNDAY, TUESDAY;THURSDAY;SUNDAY"
    })
    @DisplayName("오늘이 월 · 수 · 금이 아니면 오늘 요일과 그 뒤 2일 · 4일 요일을 요일 순으로 고른다")
    void 그_외_요일(DayOfWeek todayOfWeek, String expected) {
        // given
        LocalDate today = MONDAY_OF_WEEK.plusDays(todayOfWeek.getValue() - 1L);
        List<DayOfWeek> expectedDays = java.util.Arrays.stream(expected.split(";")).map(DayOfWeek::valueOf).toList();

        // when
        List<DayOfWeek> days = DemoLessonPlan.lessonDays(today);

        // then
        assertThat(days).containsExactlyElementsOf(expectedDays).contains(todayOfWeek);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"})
    @DisplayName("김서아 회차 7개는 모두 오늘 전이고, 일정 요일과 맞고, 오래된 순으로 서로 다르다")
    void 지난_회차_날짜(DayOfWeek todayOfWeek) {
        // given
        LocalDate today = MONDAY_OF_WEEK.plusDays(todayOfWeek.getValue() - 1L);
        List<DayOfWeek> days = DemoLessonPlan.lessonDays(today);

        // when
        List<LocalDate> dates = DemoLessonPlan.pastLessonDates(today, days, DemoLessonPlan.RECORDED_SESSION_COUNT);

        // then
        assertThat(dates).hasSize(7)
                .allSatisfy(date -> {
                    assertThat(date).isBefore(today);
                    assertThat(days).contains(date.getDayOfWeek());
                })
                .isSortedAccordingTo(LocalDate::compareTo)
                .doesNotHaveDuplicates();
        // 수업일을 건너뛰지 않았다: 마지막 회차와 오늘 사이에 다른 수업일이 없다.
        assertThat(dates.getLast().datesUntil(today).skip(1))
                .noneMatch(date -> days.contains(date.getDayOfWeek()));
    }

    @Test
    @DisplayName("진도는 회차마다 2단원씩 이어지고, 기록은 정산 회차보다 하나 적다")
    void 진도와_기록_수() {
        // given
        int last = DemoLessonPlan.RECORDED_SESSION_COUNT - 1;

        // when & then
        assertThat(DemoLessonPlan.progressStart(0)).isEqualTo(1);
        assertThat(DemoLessonPlan.progressEnd(0)).isEqualTo(2);
        assertThat(DemoLessonPlan.progressStart(1)).isEqualTo(DemoLessonPlan.progressEnd(0) + 1);
        assertThat(DemoLessonPlan.progressEnd(last)).isEqualTo(14);
        assertThat(DemoLessonPlan.RECORDED_SESSION_COUNT)
                .isEqualTo(DemoLessonPlan.SETTLEMENT_SESSION_COUNT - 1);
        assertThat(DemoLessonPlan.NOTES.getFirst().previousHomeworkResult()).isNull();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"2026-11-14, 2026", "2027-02-28, 2026", "2027-03-01, 2027"})
    @DisplayName("학년도는 3월에 바뀐다")
    void 학년도(LocalDate today, int expected) {
        // when & then
        assertThat(DemoLessonPlan.schoolYear(today)).isEqualTo(expected);
    }
}
