package com.sudam.domain.session.repository;

import com.sudam.domain.session.entity.Session;
import com.sudam.domain.session.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    boolean existsByScheduleIdAndOriginalDate(Long scheduleId, LocalDate originalDate);

    // ERD 회차 정렬 기준: session_date → start_time(NULL은 뒤로) → id
    @Query("""
            select s from Session s
            where s.cycle.id = :cycleId and s.isRecognized = true
            order by s.sessionDate, s.startTime asc nulls last, s.id
            """)
    List<Session> findRecognizedInCycle(@Param("cycleId") Long cycleId);

    // 사이클의 인정 회차 수
    long countByCycleIdAndIsRecognizedTrue(Long cycleId);

    // 사이클 인정 회차의 첫 날짜. 인정 회차가 없으면 빈 값
    @Query("select min(s.sessionDate) from Session s where s.cycle.id = :cycleId and s.isRecognized = true")
    Optional<LocalDate> findFirstRecognizedDate(@Param("cycleId") Long cycleId);

    // 여러 사이클의 인정 회차 첫 날짜를 한 쿼리로 모은다 (리포트 목록의 정산 월). 인정 회차가 없는 사이클은 결과에 없다.
    @Query("""
            select new com.sudam.domain.session.repository.CycleFirstRecognizedDate(s.cycle.id, min(s.sessionDate))
            from Session s
            where s.cycle.id in :cycleIds and s.isRecognized = true
            group by s.cycle.id
            """)
    List<CycleFirstRecognizedDate> findFirstRecognizedDates(@Param("cycleIds") Collection<Long> cycleIds);

    // 사이클 인정 회차의 마지막 날짜. 인정 회차가 없으면 빈 값
    @Query("select max(s.sessionDate) from Session s where s.cycle.id = :cycleId and s.isRecognized = true")
    Optional<LocalDate> findLastRecognizedDate(@Param("cycleId") Long cycleId);

    // 과외의 휴강 회차 중 (after, until] 날짜 범위. after가 null이면 하한 없이 until까지. 정렬은 ERD 회차 정렬 기준
    @Query("""
            select s from Session s
            where s.tutoring.id = :tutoringId and s.status = :status
              and (:after is null or s.sessionDate > :after) and s.sessionDate <= :until
            order by s.sessionDate, s.startTime asc nulls last, s.id
            """)
    List<Session> findByStatusInDateRange(@Param("tutoringId") Long tutoringId,
                                          @Param("status") SessionStatus status,
                                          @Param("after") LocalDate after,
                                          @Param("until") LocalDate until);
}
