package com.sudam.domain.session.service;

import java.time.LocalDate;

/**
 * 회차 하나를 가리키는 값. 저장된 회차는 sessionId, 저장 전 반복 일정 회차는 (scheduleId, originalDate) 중 한 가지만 쓴다.
 * 요청 본문의 조건부 필드(sessionId · scheduleId · originalDate)를 검증한 뒤 만든다.
 */
public record LessonRef(Long sessionId, Long scheduleId, LocalDate originalDate) {

    public LessonRef {
        boolean bySession = sessionId != null && scheduleId == null && originalDate == null;
        boolean bySchedule = sessionId == null && scheduleId != null && originalDate != null;
        if (!bySession && !bySchedule) {
            throw new IllegalArgumentException("sessionId 또는 (scheduleId, originalDate) 중 하나만 있어야 합니다.");
        }
    }

    public static LessonRef ofSession(Long sessionId) {
        return new LessonRef(sessionId, null, null);
    }

    public static LessonRef ofSchedule(Long scheduleId, LocalDate originalDate) {
        return new LessonRef(null, scheduleId, originalDate);
    }

    public boolean isStored() {
        return sessionId != null;
    }
}
