package com.sudam.domain.tutoring.repository;

import com.sudam.domain.tutoring.entity.TutoringPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TutoringPolicyRepository extends JpaRepository<TutoringPolicy, Long> {

    // 사이클별 적용 정책은 이 목록을 TutoringPolicies.applicableTo에 넘겨 고른다.
    @Query("""
            select p from TutoringPolicy p
            where p.tutoring.id = :tutoringId
            order by p.effectiveCycleNo, p.id
            """)
    List<TutoringPolicy> findAllByTutoringId(@Param("tutoringId") Long tutoringId);

    // 여러 과외의 정책 이력을 한 번에 (예정 회차 사이클 예측). 과외별로 묶어 TutoringPolicies.applicableTo에 넘긴다.
    List<TutoringPolicy> findAllByTutoringIdIn(Collection<Long> tutoringIds);
}
