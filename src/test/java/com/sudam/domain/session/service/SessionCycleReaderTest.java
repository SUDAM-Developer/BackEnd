package com.sudam.domain.session.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.invoice.entity.CanceledLesson;
import com.sudam.domain.session.entity.Session;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.repository.SessionRepository;
import com.sudam.domain.settlement.port.CyclePeriod;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.user.enums.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionCycleReader 사이클 회차 조회")
class SessionCycleReaderTest {

    private static final Long TUTORING_ID = 10L;
    private static final Long CYCLE_ID = 88L;
    private static final Long PREV_CYCLE_ID = 87L;
    private static final LocalDate CYCLE_END = LocalDate.of(2026, 9, 30);

    @Mock private SessionRepository sessionRepository;
    @Mock private CycleRepository cycleRepository;

    @InjectMocks
    private SessionCycleReader reader;

    private Tutoring tutoring;

    @BeforeEach
    void setUp() {
        tutoring = Tutoring.builder().subject(Subject.MATH).studentName("김민준")
                .gradeLevel(8).gradeSchoolYear(2026).build();
    }

    @Test
    @DisplayName("인정 회차 수를 센다")
    void 인정_회차_수() {
        // given
        given(sessionRepository.countByCycleIdAndIsRecognizedTrue(CYCLE_ID)).willReturn(8L);

        // when & then
        assertThat(reader.countRecognized(TUTORING_ID, CYCLE_ID)).isEqualTo(8);
    }

    @Test
    @DisplayName("기간은 첫 인정 회차 ~ 마지막 인정 회차 날짜다")
    void 기간() {
        // given
        given(sessionRepository.findFirstRecognizedDate(CYCLE_ID)).willReturn(Optional.of(LocalDate.of(2026, 9, 2)));
        given(sessionRepository.findLastRecognizedDate(CYCLE_ID)).willReturn(Optional.of(CYCLE_END));

        // when & then
        assertThat(reader.period(TUTORING_ID, CYCLE_ID)).isEqualTo(new CyclePeriod(LocalDate.of(2026, 9, 2), CYCLE_END));
    }

    @Test
    @DisplayName("인정 회차가 없으면 기간을 구할 수 없다")
    void 기간_인정_회차_없음() {
        // given
        given(sessionRepository.findFirstRecognizedDate(CYCLE_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reader.period(TUTORING_ID, CYCLE_ID)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("휴강은 직전 사이클 마지막 인정일 다음 날 ~ 이 사이클 마지막 인정일 범위에서 찾는다")
    void 휴강_직전_사이클_있음() {
        // given
        givenCycle(2);
        Cycle prev = withId(Cycle.open(tutoring, 1, LocalDateTime.of(2026, 8, 1, 0, 0)), PREV_CYCLE_ID);
        LocalDate prevEnd = LocalDate.of(2026, 9, 9);
        given(cycleRepository.findByTutoringIdAndCycleNo(TUTORING_ID, 1)).willReturn(Optional.of(prev));
        given(sessionRepository.findLastRecognizedDate(PREV_CYCLE_ID)).willReturn(Optional.of(prevEnd));
        Session canceled = Session.completeRegular(tutoring, null, null, LocalDate.of(2026, 9, 16));
        given(sessionRepository.findByStatusInDateRange(TUTORING_ID, SessionStatus.CANCELED, prevEnd, CYCLE_END))
                .willReturn(List.of(canceled));

        // when
        List<CanceledLesson> lessons = reader.canceledIn(TUTORING_ID, CYCLE_ID);

        // then: 사유 컬럼이 아직 없어 cancelReason은 null
        assertThat(lessons).containsExactly(new CanceledLesson(LocalDate.of(2026, 9, 16), null));
    }

    @Test
    @DisplayName("첫 사이클이면 하한 없이 마지막 인정일까지 찾는다")
    void 휴강_첫_사이클() {
        // given
        givenCycle(1);
        given(cycleRepository.findByTutoringIdAndCycleNo(TUTORING_ID, 0)).willReturn(Optional.empty());
        given(sessionRepository.findByStatusInDateRange(TUTORING_ID, SessionStatus.CANCELED, null, CYCLE_END))
                .willReturn(List.of());

        // when & then
        assertThat(reader.canceledIn(TUTORING_ID, CYCLE_ID)).isEmpty();
    }

    private void givenCycle(int cycleNo) {
        Cycle cycle = withId(Cycle.open(tutoring, cycleNo, LocalDateTime.of(2026, 9, 1, 0, 0)), CYCLE_ID);
        given(cycleRepository.findById(CYCLE_ID)).willReturn(Optional.of(cycle));
        given(sessionRepository.findLastRecognizedDate(CYCLE_ID)).willReturn(Optional.of(CYCLE_END));
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
