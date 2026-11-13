package com.sudam.domain.tutoring.entity;

import com.sudam.domain.tutoring.enums.ParentUpdateFrequency;
import com.sudam.domain.tutoring.enums.PresetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TutoringSetting 공유 설정 변경")
class TutoringSettingTest {

    @Test
    @DisplayName("공유 설정을 바꾸면 모든 값이 새 값으로 바뀐다")
    void 공유_설정을_바꾼다() {
        // given
        TutoringSetting setting = TutoringSetting.builder()
                .presetType(PresetType.SIMPLE)
                .parentUpdateFrequency(ParentUpdateFrequency.EVERY_SESSION)
                .homeworkNotifyEnabled(false)
                .unpaidReminderEnabled(false)
                .build();

        // when
        setting.change(PresetType.MINIMAL, ParentUpdateFrequency.AT_SETTLEMENT, true, true);

        // then
        assertThat(setting.getPresetType()).isEqualTo(PresetType.MINIMAL);
        assertThat(setting.getParentUpdateFrequency()).isEqualTo(ParentUpdateFrequency.AT_SETTLEMENT);
        assertThat(setting.isHomeworkNotifyEnabled()).isTrue();
        assertThat(setting.isUnpaidReminderEnabled()).isTrue();
    }
}
