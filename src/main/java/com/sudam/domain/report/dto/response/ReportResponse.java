package com.sudam.domain.report.dto.response;

import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.entity.SettlementReportProgress;
import com.sudam.domain.report.enums.ReportStatus;
import com.sudam.domain.report.enums.ReportTextbookStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * 정산 리포트 조회 응답. 리포트의 네 영역 중 요약(stats) · 진도(progress)만 담는다.
 */
public record ReportResponse(

        // 정산 리포트 ID
        Long reportId,

        // 리포트 상태. 발송 기능 전에는 항상 GENERATED
        ReportStatus status,

        // 학생 이름
        String studentName,

        // 사이클 기간 (첫 인정 회차 ~ 마지막 인정 회차 날짜)
        Period period,

        // 회차 · 숙제 · 이해도 요약
        Stats stats,

        // 교재별 진도 표 (표시 순서대로)
        List<Progress> progress
) {

    public record Period(LocalDate startDate, LocalDate endDate) {
    }

    public record Stats(

            // 진행한 인정 회차 수
            int completedCount,

            // 정산 회차
            int targetCount,

            // 사이클 기간 안의 휴강 수
            int canceledCount,

            // 숙제 수행률 %. 지난 숙제 결과가 하나도 없으면 null
            Integer homeworkRate,

            // 직전 사이클 리포트의 수행률. 없으면 null
            Integer prevHomeworkRate,

            // 이해도 상 · 중 · 하 횟수
            Understanding understanding
    ) {
    }

    public record Understanding(int high, int medium, int low) {
    }

    public record Progress(

            // 교재 이름 (리포트 생성 시점)
            String textbookName,

            // 이 사이클의 첫 진도 (예: "Unit 12")
            String startLabel,

            // 이 사이클의 마지막 진도
            String endLabel,

            // 기간 중 교재 추가 · 사용 종료 표기. 지금은 항상 ACTIVE
            ReportTextbookStatus textbookStatus
    ) {
    }

    /***
     * 함수 기능: 리포트 · 진도 행 · 사이클 기간으로 조회 응답을 만든다.
     * @param report 리포트 (과외를 함께 조회한 것)
     * @param progress 진도 행 (표시 순서대로)
     * @param startDate 사이클 첫 인정 회차 날짜
     * @param endDate 사이클 마지막 인정 회차 날짜
     * @return 조회 응답
     */
    public static ReportResponse of(SettlementReport report, List<SettlementReportProgress> progress,
                                    LocalDate startDate, LocalDate endDate) {
        return new ReportResponse(
                report.getId(),
                report.getStatus(),
                report.getTutoring().getStudentName(),
                new Period(startDate, endDate),
                new Stats(
                        report.getCompletedCount(),
                        report.getTargetCount(),
                        report.getCanceledCount(),
                        report.getHomeworkRate(),
                        report.getPrevHomeworkRate(),
                        new Understanding(report.getUnderstandingHigh(), report.getUnderstandingMedium(),
                                report.getUnderstandingLow())
                ),
                progress.stream()
                        .map(row -> new Progress(row.getTextbookName(), row.getStartLabel(), row.getEndLabel(),
                                row.getTextbookStatus()))
                        .toList()
        );
    }
}
