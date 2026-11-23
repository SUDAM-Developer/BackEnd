package com.sudam.domain.schedule.dto.response;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 한 달 달력 응답 (5.1.1). 달력 칸에 찍을 수업 점과 일정 네모만 담는다. 날짜를 누르면 daily로 그날 목록을 따로 부른다.
 *
 * @param yearMonth 요청한 달 (yyyy-MM)
 * @param today     서버 기준 오늘 (한국 날짜). 달력의 오늘 표시 · 처음 선택 날짜
 * @param students  범례. 수업 중 과외를 tutoringId 오름차순 — 프론트는 이 순서로 학생 색을 정한다
 * @param days      수업이나 일정이 있는 날만 날짜순
 */
public record MonthlyScheduleResponse(
        YearMonth yearMonth,
        LocalDate today,
        List<Student> students,
        List<Day> days
) {

    public record Student(Long tutoringId, String studentName) {
    }

    /**
     * 달력 하루 칸.
     *
     * @param date            날짜
     * @param lessons         그날 수업 점 (시작 시각순). 프론트는 앞 3개만 그린다
     * @param lessonCount     그날 수업 수 (점이 3개까지라 4건 이상인지 알 때)
     * @param eventCategories 그날 선생님 일정 종류, 최대 2개. teacher_events(PR-9) 전까지 빈 배열
     */
    public record Day(LocalDate date, List<Lesson> lessons, int lessonCount, List<String> eventCategories) {
    }

    /**
     * 수업 점 하나.
     *
     * @param tutoringId 과외 ID (점 색)
     * @param canceled   휴강이면 true → 회색 점
     * @param makeup     보강 회차면 true
     */
    public record Lesson(Long tutoringId, boolean canceled, boolean makeup) {
    }
}
