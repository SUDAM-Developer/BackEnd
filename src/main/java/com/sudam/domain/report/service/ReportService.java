package com.sudam.domain.report.service;

import com.sudam.domain.report.dto.response.ReportResponse;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.exception.ReportException;
import com.sudam.domain.report.exception.code.ReportErrorCode;
import com.sudam.domain.report.repository.SettlementReportProgressRepository;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.session.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 정산 리포트 조회 서비스. 리포트를 만든 과외의 선생님만 볼 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final SettlementReportRepository settlementReportRepository;
    private final SettlementReportProgressRepository settlementReportProgressRepository;
    private final SessionRepository sessionRepository;

    /***
     * 함수 기능: 정산 리포트의 요약 · 진도를 조회한다.
     * @param teacherId 로그인한 선생님 (= users.id)
     * @param reportId 정산 리포트 ID
     * @return 리포트 조회 응답
     * @throws ReportException 리포트가 없거나 다른 선생님 과외의 리포트면 REPORT404_1
     */
    public ReportResponse get(Long teacherId, Long reportId) {
        // 1. 리포트 조회: 과외를 함께 가져와 선생님을 확인한다. 남의 리포트도 '없음'으로 응답한다.
        SettlementReport report = settlementReportRepository.findWithTutoringById(reportId)
                .filter(found -> found.getTutoring().getTeacher().getId().equals(teacherId))
                .orElseThrow(() -> new ReportException(ReportErrorCode.REPORT_NOT_FOUND));

        // 2. 기간 계산: 리포트에 저장하지 않고 사이클 인정 회차의 첫 · 마지막 날짜로 구한다.
        //    리포트는 정산 회차를 채운 사이클에만 생기므로 인정 회차가 반드시 있다.
        Long cycleId = report.getCycle().getId();
        LocalDate startDate = sessionRepository.findFirstRecognizedDate(cycleId)
                .orElseThrow(() -> new IllegalStateException("사이클에 인정 회차가 없습니다. cycleId=" + cycleId));
        LocalDate endDate = sessionRepository.findLastRecognizedDate(cycleId)
                .orElseThrow(() -> new IllegalStateException("사이클에 인정 회차가 없습니다. cycleId=" + cycleId));

        // 3. 응답 변환: 진도 표를 표시 순서대로 붙인다.
        return ReportResponse.of(report,
                settlementReportProgressRepository.findAllByReportIdOrderBySortOrder(reportId), startDate, endDate);
    }
}
