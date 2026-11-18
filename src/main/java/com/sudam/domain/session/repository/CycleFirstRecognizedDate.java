package com.sudam.domain.session.repository;

import java.time.LocalDate;

/**
 * 사이클별 첫 인정 회차 날짜. 여러 사이클을 한 쿼리로 모아 조회할 때 쓰는 결과 타입이다.
 *
 * @param cycleId   사이클 ID
 * @param firstDate 그 사이클 인정 회차의 첫 수업일
 */
public record CycleFirstRecognizedDate(Long cycleId, LocalDate firstDate) {
}
