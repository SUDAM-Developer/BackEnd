package com.sudam.domain.record.repository;

import com.sudam.domain.record.entity.SessionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SessionRecordRepository extends JpaRepository<SessionRecord, Long> {

    // 사이클 인정 회차의 기록. 리포트 통계(숙제 수행률 · 이해도)에 쓴다.
    @Query("""
            select r from SessionRecord r
            where r.session.cycle.id = :cycleId and r.session.isRecognized = true
            """)
    List<SessionRecord> findAllRecognizedInCycle(@Param("cycleId") Long cycleId);
}
