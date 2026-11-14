package com.sudam.domain.record.repository;

import com.sudam.domain.record.entity.SessionProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SessionProgressRepository extends JpaRepository<SessionProgress, Long> {

    // 사이클 인정 회차의 진도를 ERD 회차 정렬 순서로. 교재는 라벨을 만들 때 쓰므로 함께 가져온다.
    @Query("""
            select p from SessionProgress p
            join fetch p.textbook
            join p.session s
            where s.cycle.id = :cycleId and s.isRecognized = true
            order by s.sessionDate, s.startTime asc nulls last, s.id
            """)
    List<SessionProgress> findAllRecognizedInCycle(@Param("cycleId") Long cycleId);
}
