package com.sudam.domain.session.repository;

import java.time.LocalDate;

/**
 * 인정 회차 1개의 사이클 계산용 값. 여러 과외의 인정 회차를 엔티티 없이 한 쿼리로 모을 때 쓰는 결과 타입이다.
 *
 * @param tutoringId 과외 ID
 * @param sessionId  회차 ID
 * @param cycleId    소속 사이클 ID
 * @param date       실제 수업 날짜
 */
public record RecognizedSession(Long tutoringId, Long sessionId, Long cycleId, LocalDate date) {
}
