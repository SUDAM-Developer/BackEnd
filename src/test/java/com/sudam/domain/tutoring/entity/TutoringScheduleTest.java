package com.sudam.domain.tutoring.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TutoringSchedule 적용 기간")
class TutoringScheduleTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    @ParameterizedTest(name = "{0}: {1} → {3}")
    @CsvSource({
            "시작 전날,              2026-08-31, true,  false",
            "시작일 당일,            2026-09-01, true,  true",
            "종료일 당일,            2026-11-30, true,  true",
            "종료 다음 날,           2026-12-01, true,  false",
            "종료일 없으면 계속 적용, 2030-01-01, false, true"
    })
    @DisplayName("시작일 · 종료일 당일을 포함해 기간 안이면 적용 중이고, 종료일이 없으면 끝없이 적용된다")
    void 적용_기간_판단(String situation, LocalDate date, boolean hasEnd, boolean expected) {
        // given
        TutoringSchedule schedule = TutoringSchedule.builder()
                .dayOfWeek(1)
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(19, 30))
                .effectiveFrom(FROM)
                .build();
        if (hasEnd) {
            ReflectionTestUtils.setField(schedule, "effectiveTo", TO);
        }

        // when
        boolean effective = schedule.isEffectiveOn(date);

        // then
        assertThat(effective).isEqualTo(expected);
    }
}
