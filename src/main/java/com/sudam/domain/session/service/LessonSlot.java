package com.sudam.domain.session.service;

import com.sudam.domain.session.entity.Session;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 예정 회차 계산기가 내보내는 회차 1개. 계산된 예정 회차(sessionId 없음)와 저장된 회차(sessionId 있음)를 같은 모양으로 담는다.
 * 저장 전 회차는 (scheduleId, originalDate)로, 저장된 회차는 sessionId로 기록 · 휴강 · 시간 바꾸기 API에 보낸다.
 *
 * @param tutoringId   과외 ID
 * @param sessionId    저장된 회차면 값, 계산된 예정 회차면 null
 * @param scheduleId   반복 일정에서 나온 회차면 값, 보강 · 추가 회차면 null
 * @param originalDate 반복 일정상 원래 날짜. 보강 · 추가 회차면 null
 * @param date         실제 수업 날짜
 * @param startTime    시작 시각. 저장 행에 시각이 없으면 반복 일정 시각으로 채운다. 그래도 없으면 null
 * @param endTime      종료 시각
 * @param sessionType  정규 · 보강 · 추가
 * @param status       계산된 예정 회차는 SCHEDULED
 * @param isRecognized 인정 회차 여부. 예정 회차는 null
 * @param cycleId      저장 행의 소속 사이클. 미인정 휴강 · 예정 회차는 null (소속은 CycleTimeline이 날짜로 계산)
 * @param timeChanged  시간을 바꾼 회차. sessions.time_changed_at 컬럼이 생기기 전까지 항상 false
 */
public record LessonSlot(
        Long tutoringId,
        Long sessionId,
        Long scheduleId,
        LocalDate originalDate,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        SessionType sessionType,
        SessionStatus status,
        Boolean isRecognized,
        Long cycleId,
        boolean timeChanged
) {

    /***
     * 함수 기능: 반복 일정의 한 날짜를 저장 전 예정 회차로 만든다.
     * @param rule 반복 일정
     * @param date 수업 날짜 (= 원래 날짜)
     * @return REGULAR · SCHEDULED 예정 회차
     */
    static LessonSlot planned(ScheduleRule rule, LocalDate date) {
        return new LessonSlot(rule.tutoringId(), null, rule.scheduleId(), date, date, rule.startTime(),
                rule.endTime(), SessionType.REGULAR, SessionStatus.SCHEDULED, null, null, false);
    }

    /***
     * 함수 기능: 저장된 회차 행을 회차로 옮긴다. 시각은 행에 있는 값 그대로라 정규 기록이면 null일 수 있다.
     *          연관 엔티티는 ID만 읽으므로 지연 로딩 프록시를 초기화하지 않는다.
     * @param session 저장된 회차
     * @return 저장 행 회차
     */
    static LessonSlot stored(Session session) {
        Long scheduleId = session.getSchedule() == null ? null : session.getSchedule().getId();
        Long cycleId = session.getCycle() == null ? null : session.getCycle().getId();
        // 시간 변경 컬럼(time_changed_at)은 강민 휴강 · 시간 변경 작업에서 추가된다. 그때 이 값을 채운다.
        return new LessonSlot(session.getTutoring().getId(), session.getId(), scheduleId, session.getOriginalDate(),
                session.getSessionDate(), session.getStartTime(), session.getEndTime(), session.getSessionType(),
                session.getStatus(), session.getIsRecognized(), cycleId, false);
    }

    LessonSlot withTimesIfAbsent(LocalTime start, LocalTime end) {
        if (startTime != null) {
            return this;
        }
        return new LessonSlot(tutoringId, sessionId, scheduleId, originalDate, date, start, end, sessionType,
                status, isRecognized, cycleId, timeChanged);
    }

    public boolean isStored() {
        return sessionId != null;
    }

    /***
     * 함수 기능: 이 회차가 주어진 시간대와 겹치는지 확인한다. 끝과 시작이 맞닿는 것은 겹치지 않는다.
     *          시간 바꾸기 · 보강 후보 겹침 검사가 같은 규칙을 쓴다. 휴강 회차를 뺄지는 호출하는 쪽이 정한다.
     * @param start 확인할 시작 시각
     * @param end 확인할 종료 시각
     * @return 겹치면 true. 이 회차에 시각이 없으면 false
     */
    public boolean overlaps(LocalTime start, LocalTime end) {
        if (startTime == null || endTime == null) {
            return false;
        }
        return startTime.isBefore(end) && start.isBefore(endTime);
    }

    // CycleTimeline이 예측 결과를 찾는 키. 저장된 회차는 sessionId, 계산된 회차는 (scheduleId, originalDate).
    Key key() {
        return isStored() ? new Key(sessionId, null, null) : new Key(null, scheduleId, originalDate);
    }

    record Key(Long sessionId, Long scheduleId, LocalDate originalDate) {
    }
}
