package com.sudam.domain.record.repository;

import com.sudam.domain.record.entity.SessionProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SessionProgressRepository extends JpaRepository<SessionProgress, Long> {

    // 과외의 기준 날짜보다 앞선 회차의 진도를 최근 회차부터. 교재별 첫 행이 그 교재의 지난 끝 진도다.
    @Query("""
            select new com.sudam.domain.record.repository.TextbookLastPosition(p.textbook.id, p.endPosition)
            from SessionProgress p
            join p.session s
            where s.tutoring.id = :tutoringId and s.sessionDate < :date
            order by s.sessionDate desc, s.startTime desc nulls first, s.id desc
            """)
    List<TextbookLastPosition> findPositionsBefore(@Param("tutoringId") Long tutoringId,
                                                   @Param("date") LocalDate date);

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
