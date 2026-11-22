package com.sudam.domain.session.repository;

import java.time.LocalDate;

/**
 * 과외별 마지막 정산 사이클의 마지막 인정 날짜. 이 날짜까지는 기록 · 휴강이 막혀 있어 예정 회차를 만들지 않는다.
 *
 * @param tutoringId 과외 ID
 * @param lastDate   정산한 사이클의 인정 회차 중 가장 늦은 수업일
 */
public record TutoringLastSettledDate(Long tutoringId, LocalDate lastDate) {
}
