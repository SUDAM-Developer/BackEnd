package com.sudam.domain.cycle.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.exception.CycleException;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import com.sudam.global.exception.ProjectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CycleOpener")
class CycleOpenerTest {

    private static final Long TUTORING_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 18, 0);
    private static final TeacherProfile TEACHER = TeacherProfile.create(
            User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app"), "한서윤", List.of(Subject.MATH), null);

    @Mock
    private CycleRepository cycleRepository;

    @InjectMocks
    private CycleOpener cycleOpener;

    private Tutoring tutoring;

    @BeforeEach
    void setUp() {
        tutoring = Tutoring.builder()
                .teacher(TEACHER)
                .subjects(List.of(Subject.MATH))
                .studentName("김서아")
                .gradeLevel(9)
                .gradeSchoolYear(2026)
                .build();
        ReflectionTestUtils.setField(tutoring, "id", TUTORING_ID);
    }

    @Test
    @DisplayName("사이클이 없으면 1번 사이클을 진행 중으로 열고 저장한다")
    void 사이클이_없으면_1번을_연다() {
        // given
        given(cycleRepository.findTopByTutoringIdOrderByCycleNoDesc(TUTORING_ID)).willReturn(Optional.empty());
        given(cycleRepository.save(any(Cycle.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Cycle opened = cycleOpener.openNext(tutoring, NOW);

        // then
        assertThat(opened.getCycleNo()).isEqualTo(1);
        assertThat(opened.isInProgress()).isTrue();
        assertThat(opened.getTutoring()).isSameAs(tutoring);
        assertThat(opened.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("마지막 사이클이 정산 완료면 그 번호 + 1로 열고, 받은 시각을 그대로 시작 시각으로 쓴다")
    void 마지막이_정산_완료면_다음_번호로_연다() {
        // given
        Cycle settled = Cycle.open(tutoring, 3, NOW.minusWeeks(4));
        settled.settle(NOW);
        given(cycleRepository.findTopByTutoringIdOrderByCycleNoDesc(TUTORING_ID)).willReturn(Optional.of(settled));
        given(cycleRepository.save(any(Cycle.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Cycle opened = cycleOpener.openNext(tutoring, NOW);

        // then
        assertThat(opened.getCycleNo()).isEqualTo(4);
        assertThat(opened.isInProgress()).isTrue();
        assertThat(opened.getStartedAt()).isEqualTo(settled.getSettledAt());
    }

    @Test
    @DisplayName("진행 중 사이클이 있으면 CYCLE409_2 예외를 던지고 저장하지 않는다")
    void 진행_중_사이클이_있으면_예외() {
        // given
        Cycle inProgress = Cycle.open(tutoring, 2, NOW.minusWeeks(2));
        given(cycleRepository.findTopByTutoringIdOrderByCycleNoDesc(TUTORING_ID)).willReturn(Optional.of(inProgress));

        // when & then
        assertThatThrownBy(() -> cycleOpener.openNext(tutoring, NOW))
                .isInstanceOf(CycleException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo("CYCLE409_2");
        verify(cycleRepository, never()).save(any());
    }

    @Test
    @DisplayName("잠근 트랜잭션 밖에서 부르지 못하도록 propagation = MANDATORY다")
    void 트랜잭션_전파는_MANDATORY다() {
        // when
        Transactional transactional = CycleOpener.class.getAnnotation(Transactional.class);

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
