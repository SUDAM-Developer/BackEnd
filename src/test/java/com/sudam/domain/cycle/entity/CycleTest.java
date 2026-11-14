package com.sudam.domain.cycle.entity;

import com.sudam.domain.cycle.enums.CycleStatus;
import com.sudam.domain.cycle.exception.CycleException;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.global.exception.ProjectException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cycle 엔티티")
class CycleTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);
    private static final LocalDateTime SETTLED_AT = LocalDateTime.of(2026, 11, 14, 18, 0);

    @Nested
    @DisplayName("open")
    class Open {

        @Test
        @DisplayName("진행 중 상태로 열리고 정산 시각은 비어 있다")
        void 진행_중으로_열린다() {
            // given
            Tutoring tutoring = tutoring();

            // when
            Cycle cycle = Cycle.open(tutoring, 1, STARTED_AT);

            // then
            assertThat(cycle.getTutoring()).isSameAs(tutoring);
            assertThat(cycle.getCycleNo()).isEqualTo(1);
            assertThat(cycle.getStatus()).isEqualTo(CycleStatus.IN_PROGRESS);
            assertThat(cycle.isInProgress()).isTrue();
            assertThat(cycle.getStartedAt()).isEqualTo(STARTED_AT);
            assertThat(cycle.getSettledAt()).isNull();
        }
    }

    @Nested
    @DisplayName("settle")
    class Settle {

        @Test
        @DisplayName("진행 중 사이클을 정산하면 SETTLED가 되고 정산 시각이 남는다")
        void 정산하면_SETTLED가_된다() {
            // given
            Cycle cycle = Cycle.open(tutoring(), 1, STARTED_AT);

            // when
            cycle.settle(SETTLED_AT);

            // then
            assertThat(cycle.getStatus()).isEqualTo(CycleStatus.SETTLED);
            assertThat(cycle.isInProgress()).isFalse();
            assertThat(cycle.getSettledAt()).isEqualTo(SETTLED_AT);
        }

        @Test
        @DisplayName("이미 정산된 사이클을 다시 정산하면 CYCLE409_1 예외가 발생하고 정산 시각은 바뀌지 않는다")
        void 두_번_정산하면_예외() {
            // given
            Cycle cycle = Cycle.open(tutoring(), 1, STARTED_AT);
            cycle.settle(SETTLED_AT);

            // when & then
            assertThatThrownBy(() -> cycle.settle(SETTLED_AT.plusDays(1)))
                    .isInstanceOf(CycleException.class)
                    .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                    .isEqualTo("CYCLE409_1");
            assertThat(cycle.getSettledAt()).isEqualTo(SETTLED_AT);
        }
    }

    private static Tutoring tutoring() {
        return Tutoring.builder()
                .teacherId(1L)
                .subjectId((short) 2)
                .studentName("김민준")
                .gradeLevel(8)
                .gradeSchoolYear(2026)
                .build();
    }
}
