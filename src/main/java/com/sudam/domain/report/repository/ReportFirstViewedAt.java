package com.sudam.domain.report.repository;

import java.time.LocalDateTime;

/**
 * 리포트별 첫 열람 시각. 여러 리포트를 한 쿼리로 모아 조회할 때 쓰는 결과 타입이다.
 *
 * @param reportId      정산 리포트 ID
 * @param firstViewedAt 참여자 누구든 처음 연 시각
 */
public record ReportFirstViewedAt(Long reportId, LocalDateTime firstViewedAt) {
}
