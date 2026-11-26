package com.sudam.domain.record.repository;

import com.sudam.domain.record.entity.SessionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SessionRecordRepository extends JpaRepository<SessionRecord, Long> {

    // 과외의 기준 날짜보다 앞선 직전 기록의 다음 숙제. 기록이 없거나 숙제가 비어 있으면 빈 값이다.
    @Query("""
            select r.nextHomework
            from SessionRecord r
            join r.session s
            where s.tutoring.id = :tutoringId and s.sessionDate < :date
            order by s.sessionDate desc, s.startTime desc nulls first, s.id desc
            limit 1
            """)
    Optional<String> findPreviousHomework(@Param("tutoringId") Long tutoringId,
                                          @Param("date") LocalDate date);

    // 사이클 인정 회차의 기록. 리포트 통계(숙제 수행률 · 이해도)에 쓴다.
    @Query("""
            select r from SessionRecord r
            where r.session.cycle.id = :cycleId and r.session.isRecognized = true
            """)
    List<SessionRecord> findAllRecognizedInCycle(@Param("cycleId") Long cycleId);

    // 기록이 있는 회차 ID. 일정 탭 회차 카드의 '기록됨' · [기록완료]를 회차 수와 상관없이 한 번에 확인한다.
    @Query("""
            select r.session.id from SessionRecord r
            where r.session.id in :sessionIds
            """)
    Set<Long> findRecordedSessionIds(@Param("sessionIds") Collection<Long> sessionIds);
}
