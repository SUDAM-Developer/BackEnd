package com.sudam.domain.session.service;

import com.sudam.domain.tutoring.entity.Tutoring;

import java.time.LocalDate;

/**
 * 과외 1개에서 예정 회차를 만들 수 있는 날짜 범위.
 *
 * @param startDate   과외 생성일. 이 날짜 이전은 만들지 않는다
 * @param endDate     수업 종료일 (포함). 진행 중이면 null
 * @param lockedUntil 마지막 정산 사이클의 마지막 인정 날짜 (포함). 이 날짜까지는 계산 회차를 만들지 않는다. 정산 전이면 null
 */
public record TutoringSpan(Long tutoringId, LocalDate startDate, LocalDate endDate, LocalDate lockedUntil) {

    static TutoringSpan of(Tutoring tutoring, LocalDate lockedUntil) {
        LocalDate endDate = tutoring.getEndedAt() == null ? null : tutoring.getEndedAt().toLocalDate();
        return new TutoringSpan(tutoring.getId(), tutoring.getCreatedAt().toLocalDate(), endDate, lockedUntil);
    }

    // 과외 기간 [startDate, endDate] 안인지. 정산 잠금(lockedUntil)은 보지 않는다.
    boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
    }

    boolean isLocked(LocalDate date) {
        return lockedUntil != null && !date.isAfter(lockedUntil);
    }
}
