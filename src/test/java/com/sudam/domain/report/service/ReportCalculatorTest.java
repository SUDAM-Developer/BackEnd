package com.sudam.domain.report.service;

import com.sudam.domain.record.enums.HomeworkResult;
import com.sudam.domain.tutoring.entity.Textbook;
import com.sudam.domain.tutoring.enums.ProgressUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

import static com.sudam.domain.record.enums.HomeworkResult.DONE;
import static com.sudam.domain.record.enums.HomeworkResult.NOT_DONE;
import static com.sudam.domain.record.enums.HomeworkResult.PARTIAL;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportCalculator 리포트 통계 · 진도 라벨 계산")
class ReportCalculatorTest {

    @Test
    @DisplayName("숙제 수행률은 (DONE + PARTIAL × 0.5) ÷ 결과 수를 반올림한다")
    void 숙제_수행률() {
        // given: DONE 6, PARTIAL 1, NOT_DONE 1 → 6.5 / 8 = 81.25%
        List<HomeworkResult> results = List.of(DONE, DONE, DONE, DONE, DONE, DONE, PARTIAL, NOT_DONE);

        // when
        Integer rate = ReportCalculator.homeworkRate(results);

        // then
        assertThat(rate).isEqualTo(81);
    }

    @Test
    @DisplayName("결과가 없는 기록(null)은 분모에서 뺀다")
    void 결과_없는_기록_제외() {
        // given: 첫 수업은 지난 숙제가 없어 null. DONE 1, PARTIAL 1 → 1.5 / 2 = 75%
        List<HomeworkResult> results = Arrays.asList(null, DONE, PARTIAL);

        // when
        Integer rate = ReportCalculator.homeworkRate(results);

        // then
        assertThat(rate).isEqualTo(75);
    }

    @Test
    @DisplayName("결과가 하나도 없으면 null")
    void 결과_없음() {
        // given
        List<HomeworkResult> results = Arrays.asList(null, null);

        // when
        Integer rate = ReportCalculator.homeworkRate(results);

        // then
        assertThat(rate).isNull();
    }

    @Test
    @DisplayName("소수점 .5는 올림한다 (12.5% → 13%)")
    void 반올림_경계() {
        // given: DONE 1, PARTIAL 0, NOT_DONE 7 → 1 / 8 = 12.5% → 13
        List<HomeworkResult> results = List.of(DONE, NOT_DONE, NOT_DONE, NOT_DONE, NOT_DONE, NOT_DONE, NOT_DONE,
                NOT_DONE);

        // when
        Integer rate = ReportCalculator.homeworkRate(results);

        // then
        assertThat(rate).isEqualTo(13);
    }

    @ParameterizedTest(name = "{0} {1} → {3}")
    @CsvSource({
            "UNIT, , 12, Unit 12",
            "CHAPTER, , 3, Chapter 3",
            "SECTION, , 3, 3단원",
            "PAGE, , 45, 45p",
            "ROUND, , 3, 3회",
            "CUSTOM, 강, 9, 9강"
    })
    @DisplayName("진도 라벨은 교재 단위에 맞춰 만든다")
    void 진도_라벨(ProgressUnit unit, String customLabel, int position, String expected) {
        // given
        Textbook textbook = Textbook.builder().name("교재").progressUnit(unit).customUnitLabel(customLabel).build();

        // when
        String label = ReportCalculator.progressLabel(textbook, position);

        // then
        assertThat(label).isEqualTo(expected);
    }
}
