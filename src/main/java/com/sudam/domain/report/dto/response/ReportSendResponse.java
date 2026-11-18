package com.sudam.domain.report.dto.response;

import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.enums.ReportStatus;

import java.time.LocalDateTime;

/**
 * 정산 리포트 발송 확인 응답.
 *
 * @param reportId 정산 리포트 ID
 * @param status   SENT
 * @param sentAt   첫 발송 확인 시각 (다시 보내도 바뀌지 않는다)
 */
public record ReportSendResponse(Long reportId, ReportStatus status, LocalDateTime sentAt) {

    public static ReportSendResponse from(SettlementReport report) {
        return new ReportSendResponse(report.getId(), report.getStatus(), report.getSentAt());
    }
}
