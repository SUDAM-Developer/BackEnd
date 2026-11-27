package com.sudam.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolYearTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"2026-02-28, 2025", "2026-03-01, 2026", "2026-12-31, 2026", "2027-01-01, 2026"})
    @DisplayName("학년도는 3월에 바뀐다")
    void 학년도_경계(LocalDate today, int expected) {
        // when
        int schoolYear = SchoolYear.of(today);

        // then
        assertThat(schoolYear).isEqualTo(expected);
    }
}
