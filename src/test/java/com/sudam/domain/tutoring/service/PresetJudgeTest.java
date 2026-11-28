package com.sudam.domain.tutoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sudam.domain.tutoring.enums.ParentUpdateFrequency;
import com.sudam.domain.tutoring.enums.PresetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class PresetJudgeTest {

    @ParameterizedTest
    @CsvSource({"EVERY_SESSION,THOROUGH", "AT_SETTLEMENT,SIMPLE", "NONE,MINIMAL"})
    @DisplayName("근황 주기로 유형을 정한다")
    void 근황_주기별_유형(ParentUpdateFrequency answer, PresetType expected) {
        // given / when
        PresetType result = PresetJudge.judge(false, answer);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(ParentUpdateFrequency.class)
    @DisplayName("건너뛰면 답과 무관하게 SIMPLE")
    void 건너뛰면_SIMPLE(ParentUpdateFrequency answer) {
        // given / when / then
        assertThat(PresetJudge.judge(true, answer)).isEqualTo(PresetType.SIMPLE);
    }

    @Test
    @DisplayName("건너뛰면 답이 null이어도 SIMPLE")
    void 건너뛰면_null이어도_SIMPLE() {
        // given / when / then
        assertThat(PresetJudge.judge(true, null)).isEqualTo(PresetType.SIMPLE);
    }
}
