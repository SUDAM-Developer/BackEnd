package com.sudam.domain.session.repository;

import com.sudam.domain.session.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SessionRepository extends JpaRepository<Session, Long> {

    boolean existsByScheduleIdAndOriginalDate(Long scheduleId, LocalDate originalDate);

    // ERD 회차 정렬 기준: session_date → start_time(NULL은 뒤로) → id
    @Query("""
            select s from Session s
            where s.cycle.id = :cycleId and s.isRecognized = true
            order by s.sessionDate, s.startTime asc nulls last, s.id
            """)
    List<Session> findRecognizedInCycle(@Param("cycleId") Long cycleId);
}
