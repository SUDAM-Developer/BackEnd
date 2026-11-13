package com.sudam.domain.tutoring.entity;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 정책 이력에서 사이클에 적용할 행을 고르는 순수 함수 모음. DB 없이 테스트할 수 있게 엔티티 밖에 둔다.
 */
public final class TutoringPolicies {

    // 같은 사이클에서 시작하는 행이 여럿이면 나중에 추가된(id가 큰) 행이 이긴다. 저장 전 행(id NULL)은 가장 최근으로 본다.
    private static final Comparator<TutoringPolicy> LATEST_LAST = Comparator
            .comparing(TutoringPolicy::getEffectiveCycleNo)
            .thenComparing(TutoringPolicy::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private TutoringPolicies() {
    }

    /***
     * 함수 기능: 사이클 N에 적용할 정책을 고른다.
     *          effective_cycle_no ≤ N인 행 중 가장 최근 것, effective_cycle_no가 같으면 id가 큰 행 (ERD 3. 과외 도메인).
     * @param policies 한 과외의 정책 이력 (순서 무관)
     * @param cycleNo 사이클 번호
     * @return 적용할 정책. 해당 사이클 이전에 시작한 정책이 없으면 빈 값
     */
    public static Optional<TutoringPolicy> applicableTo(List<TutoringPolicy> policies, int cycleNo) {
        return policies.stream()
                .filter(policy -> policy.getEffectiveCycleNo() <= cycleNo)
                .max(LATEST_LAST);
    }
}
