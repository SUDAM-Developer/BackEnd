package com.sudam.domain.report.dto.response;

import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.enums.ReportStatus;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * 정산 리포트 목록(발송 현황) 응답. 리포트 목록 화면(T-13)에서 리포트마다 보냈는지를 보여준다.
 *
 * @param items 리포트 목록 (최근에 만든 순)
 */
public record ReportDeliveryListResponse(List<Item> items) {

    /**
     * 리포트 한 건.
     *
     * @param reportId        정산 리포트 ID
     * @param tutoringId      과외 ID
     * @param studentName     학생 이름
     * @param settlementMonth 정산 월 (yyyy-MM). 사이클 첫 인정 회차의 월, 인정 회차가 없으면 사이클 시작월
     * @param status          GENERATED / SENT
     * @param sentAt          첫 발송 확인 시각. 보내지 않았으면 null
     * @param createdAt       리포트를 만든 시각 (정산 시각)
     */
    public record Item(
            Long reportId,
            Long tutoringId,
            String studentName,
            YearMonth settlementMonth,
            ReportStatus status,
            LocalDateTime sentAt,
            LocalDateTime createdAt
    ) {

        public static Item of(SettlementReport report, YearMonth settlementMonth) {
            return new Item(report.getId(), report.getTutoring().getId(), report.getTutoring().getStudentName(),
                    settlementMonth, report.getStatus(), report.getSentAt(), report.getCreatedAt());
        }
    }
}
