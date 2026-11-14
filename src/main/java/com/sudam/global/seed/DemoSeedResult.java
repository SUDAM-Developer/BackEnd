package com.sudam.global.seed;

import com.sudam.domain.user.enums.Role;

import java.time.LocalDate;

/**
 * 가상 데이터 실행 결과. 실행기가 로그 · 토큰 발급에 쓴다.
 *
 * @param created           이번 실행에서 데이터를 만들었는지 (false = 이미 있어 건너뜀)
 * @param teacherUserId     시드 선생님 users.id
 * @param teacherRole       시드 선생님 역할 (토큰 발급용)
 * @param demoTutoringId    김서아 과외 ID. 건너뛰었으면 NULL
 * @param todayScheduleId   김서아의 오늘 요일 일정 ID (기록 저장 요청의 scheduleId). 건너뛰었으면 NULL
 * @param today             실행일 (기록 저장 요청의 originalDate). 건너뛰었으면 NULL
 */
public record DemoSeedResult(
        boolean created,
        Long teacherUserId,
        Role teacherRole,
        Long demoTutoringId,
        Long todayScheduleId,
        LocalDate today
) {

    static DemoSeedResult skipped(Long teacherUserId, Role teacherRole) {
        return new DemoSeedResult(false, teacherUserId, teacherRole, null, null, null);
    }

    static DemoSeedResult created(Long teacherUserId, Role teacherRole, Long demoTutoringId, Long todayScheduleId,
                                  LocalDate today) {
        return new DemoSeedResult(true, teacherUserId, teacherRole, demoTutoringId, todayScheduleId, today);
    }
}
