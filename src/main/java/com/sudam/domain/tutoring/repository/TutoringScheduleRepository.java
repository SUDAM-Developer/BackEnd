package com.sudam.domain.tutoring.repository;

import com.sudam.domain.tutoring.entity.TutoringSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface TutoringScheduleRepository extends JpaRepository<TutoringSchedule, Long> {

    // 과외들의 반복 일정 중 적용 기간이 [from, to]와 겹치는 것. 예정 회차 계산기(LessonCalendar)가 펼친다.
    @Query("""
            select s from TutoringSchedule s
            where s.tutoring.id in :tutoringIds
              and s.effectiveFrom <= :to and (s.effectiveTo is null or s.effectiveTo >= :from)
            """)
    List<TutoringSchedule> findEffectiveIn(@Param("tutoringIds") Collection<Long> tutoringIds,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);
}
