package com.sudam.domain.settlement.port;

import java.time.LocalDate;

/**
 * 사이클 기간 = 그 사이클 인정 회차의 MIN(session_date) ~ MAX(session_date) (ERD 5. 회차 · 사이클 계산 규칙).
 *
 * @param start 첫 인정 회차 날짜
 * @param end   마지막 인정 회차 날짜
 */
public record CyclePeriod(LocalDate start, LocalDate end) {
}
