package com.sudam.domain.tutoring.entity;

import com.sudam.domain.tutoring.enums.PaymentTiming;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("사이클별 적용 정책 선택")
class TutoringPoliciesTest {

    @Test
    @DisplayName("사이클 N 이전에 시작한 정책 중 시작 사이클이 가장 늦은 행을 고른다")
    void 시작_사이클이_가장_늦은_행을_고른다() {
        // given
        TutoringPolicy first = policy(1L, 1, 300_000);
        TutoringPolicy second = policy(2L, 3, 350_000);
        TutoringPolicy future = policy(3L, 5, 400_000);

        // when
        Optional<TutoringPolicy> result = TutoringPolicies.applicableTo(List.of(future, first, second), 4);

        // then
        assertThat(result).containsSame(second);
    }

    @Test
    @DisplayName("시작 사이클이 N과 같은 행도 사이클 N에 적용된다")
    void 시작_사이클이_같으면_적용된다() {
        // given
        TutoringPolicy first = policy(1L, 1, 300_000);
        TutoringPolicy next = policy(2L, 2, 350_000);

        // when
        Optional<TutoringPolicy> result = TutoringPolicies.applicableTo(List.of(first, next), 2);

        // then
        assertThat(result).containsSame(next);
    }

    @Test
    @DisplayName("같은 사이클에서 시작하는 행이 여럿이면 id가 큰 행을 고른다")
    void 같은_사이클이면_id가_큰_행을_고른다() {
        // given
        TutoringPolicy older = policy(4L, 2, 300_000);
        TutoringPolicy newer = policy(7L, 2, 320_000);
        TutoringPolicy oldest = policy(1L, 1, 280_000);

        // when — 입력 순서와 무관해야 한다
        Optional<TutoringPolicy> result = TutoringPolicies.applicableTo(List.of(newer, oldest, older), 3);

        // then
        assertThat(result).containsSame(newer);
    }

    @Test
    @DisplayName("미래 사이클에서 시작하는 행만 있으면 빈 값을 돌려준다")
    void 미래_사이클_행만_있으면_빈_값() {
        // given
        List<TutoringPolicy> policies = List.of(policy(1L, 3, 300_000), policy(2L, 5, 350_000));

        // when
        Optional<TutoringPolicy> result = TutoringPolicies.applicableTo(policies, 2);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("정책 이력이 비어 있으면 빈 값을 돌려준다")
    void 이력이_없으면_빈_값() {
        // when
        Optional<TutoringPolicy> result = TutoringPolicies.applicableTo(List.of(), 1);

        // then
        assertThat(result).isEmpty();
    }

    private static TutoringPolicy policy(Long id, int effectiveCycleNo, int tuitionFee) {
        TutoringPolicy policy = TutoringPolicy.builder()
                .effectiveCycleNo(effectiveCycleNo)
                .settlementSessionCnt(8)
                .paymentTiming(PaymentTiming.POSTPAID)
                .tuitionFee(tuitionFee)
                .bankCode("004")
                .bankName("국민")
                .accountNumber("12345678901492")
                .accountHolder("한서윤")
                .build();
        // id는 DB가 부여하므로 테스트에서만 직접 넣는다.
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }
}
