package com.sudam.domain.tutoring.repository;

import com.sudam.domain.tutoring.entity.TutoringPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TutoringPolicyRepository extends JpaRepository<TutoringPolicy, Long> {

    // 사이클별 적용 정책은 이 목록을 TutoringPolicies.applicableTo에 넘겨 고른다.
    @Query("""
            select p from TutoringPolicy p
            where p.tutoring.id = :tutoringId
            order by p.effectiveCycleNo, p.id
            """)
    List<TutoringPolicy> findAllByTutoringId(@Param("tutoringId") Long tutoringId);
}
