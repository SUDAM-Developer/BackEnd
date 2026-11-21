package com.sudam.domain.session.service;

import com.sudam.domain.tutoring.entity.TutoringSchedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 반복 일정 1행을 계산에 필요한 값만 담아 옮긴 것. 엔티티 없이 펼치기 규칙을 테스트하려고 둔다.
 *
 * @param effectiveTo 적용 종료일 (포함). null이면 현재 적용 중
 */
public record ScheduleRule(
        Long scheduleId,
        Long tutoringId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {

    static ScheduleRule from(TutoringSchedule schedule) {
        return new ScheduleRule(schedule.getId(), schedule.getTutoring().getId(),
                DayOfWeek.of(schedule.getDayOfWeek()), schedule.getStartTime(), schedule.getEndTime(),
                schedule.getEffectiveFrom(), schedule.getEffectiveTo());
    }

    // 요일이 같고 적용 기간 [effectiveFrom, effectiveTo] 안이면 이 일정의 수업 날짜다.
    boolean occursOn(LocalDate date) {
        return date.getDayOfWeek() == dayOfWeek
                && !date.isBefore(effectiveFrom)
                && (effectiveTo == null || !date.isAfter(effectiveTo));
    }
}
