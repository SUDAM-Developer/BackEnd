package com.sudam.domain.report.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.participant.enums.ParticipantRole;
import com.sudam.domain.participant.repository.ParticipantRepository;
import com.sudam.domain.report.dto.response.ReportDeliveryListResponse;
import com.sudam.domain.report.dto.response.ReportRecipientListResponse;
import com.sudam.domain.report.dto.response.ReportResponse;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.exception.ReportException;
import com.sudam.domain.report.exception.code.ReportErrorCode;
import com.sudam.domain.report.repository.SettlementReportProgressRepository;
import com.sudam.domain.report.repository.ReportFirstViewedAt;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.report.repository.SettlementReportViewRepository;
import com.sudam.domain.session.repository.CycleFirstRecognizedDate;
import com.sudam.domain.session.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final SettlementReportViewRepository settlementReportViewRepository;
    private final ParticipantRepository participantRepository;
    private final ReportShareUrlFactory reportShareUrlFactory;

    /***
     * 함수 기능: 선생님의 정산 리포트 목록(발송 현황)을 최근에 만든 순으로 조회한다.
     * @param teacherId 로그인한 선생님 (= users.id)
     * @return 리포트 목록 응답. 리포트가 없으면 빈 목록
     */
    public ReportDeliveryListResponse listDeliveries(Long teacherId) {
        List<SettlementReport> reports = settlementReportRepository.findAllOfTeacher(teacherId);
        if (reports.isEmpty()) {
            return new ReportDeliveryListResponse(List.of());
        }

        // 리포트마다 첫 인정 회차를 따로 조회하지 않도록 사이클 ID로 한 번에 모은다.
        List<Long> cycleIds = reports.stream().map(report -> report.getCycle().getId()).toList();
        Map<Long, LocalDate> firstDateByCycleId = sessionRepository.findFirstRecognizedDates(cycleIds).stream()
                .collect(Collectors.toMap(CycleFirstRecognizedDate::cycleId, CycleFirstRecognizedDate::firstDate));

        // 열람 시각도 리포트 ID로 한 번에 모은다. 기록이 없는 리포트는 null(안 읽음)이 된다.
        List<Long> reportIds = reports.stream().map(SettlementReport::getId).toList();
        Map<Long, LocalDateTime> viewedAtByReportId = settlementReportViewRepository.findFirstViewedAts(reportIds)
                .stream()
                .collect(Collectors.toMap(ReportFirstViewedAt::reportId, ReportFirstViewedAt::firstViewedAt));

        return new ReportDeliveryListResponse(reports.stream()
                .map(report -> ReportDeliveryListResponse.Item.of(report,
                        settlementMonth(firstDateByCycleId.get(report.getCycle().getId()), report.getCycle()),
                        viewedAtByReportId.get(report.getId())))
                .toList());
    }

    // 정산 월: 사이클 인정 회차가 시작된 달. 사이클은 직전 정산 순간 열리므로 시작 시각은 앞 달 말일일 수 있어
    // 첫 인정 회차를 우선하고, 인정 회차가 없을 때만 사이클 시작월로 대신한다.
    private static YearMonth settlementMonth(LocalDate firstRecognizedDate, Cycle cycle) {
        return firstRecognizedDate != null
                ? YearMonth.from(firstRecognizedDate)
                : YearMonth.from(cycle.getStartedAt());
    }

    /***
     * 함수 기능: 정산 리포트의 요약 · 진도를 조회한다.
     * @param teacherId 로그인한 선생님 (= users.id)
     * @param reportId 정산 리포트 ID
     * @return 리포트 조회 응답
     * @throws ReportException 리포트가 없거나 다른 선생님 과외의 리포트면 REPORT404_1
     */
    public ReportResponse get(Long teacherId, Long reportId) {
        return toResponse(findOwned(teacherId, reportId));
    }

    /***
     * 함수 기능: 리포트를 받을 학부모와 각자의 공유 URL을 조회한다. 프론트가 카카오톡 공유 메시지에 넣는다.
     *          삭제되지 않았고 링크 토큰이 있는 학부모만 담는다.
     * @param teacherId 로그인한 선생님 (= users.id)
     * @param reportId  정산 리포트 ID
     * @return 받는 사람 목록 (등록 순). 링크를 받을 학부모가 없으면 빈 목록
     * @throws ReportException 리포트가 없거나 다른 선생님 과외의 리포트면 REPORT404_1
     */
    public ReportRecipientListResponse listRecipients(Long teacherId, Long reportId) {
        SettlementReport report = findOwned(teacherId, reportId);
        return new ReportRecipientListResponse(
                participantRepository.findLinkHolders(report.getTutoring().getId(), ParticipantRole.PARENT).stream()
                        .map(parent -> new ReportRecipientListResponse.Item(parent.getId(), parent.getLabel(),
                                reportShareUrlFactory.reportUrl(parent.getLinkToken(), reportId)))
                        .toList());
    }

    // 리포트와 과외를 함께 가져와 선생님을 확인한다. 남의 리포트도 '없음'으로 응답한다.
    private SettlementReport findOwned(Long teacherId, Long reportId) {
        return settlementReportRepository.findWithTutoringById(reportId)
                .filter(found -> found.getTutoring().getTeacher().getId().equals(teacherId))
                .orElseThrow(() -> new ReportException(ReportErrorCode.REPORT_NOT_FOUND));
    }

    /***
     * 함수 기능: 리포트를 조회 응답으로 바꾼다. 선생님 조회와 학부모 링크 조회가 같이 쓴다.
     *          기간은 리포트에 저장하지 않고 사이클 인정 회차의 첫 · 마지막 날짜로 구한다.
     *          리포트는 정산 회차를 채운 사이클에만 생기므로 인정 회차가 반드시 있다.
     * @param report 과외를 함께 조회한 리포트
     * @return 요약 · 기간 · 진도(표시 순서) · 이해도 수준
     */
    ReportResponse toResponse(SettlementReport report) {
        Long cycleId = report.getCycle().getId();
        LocalDate startDate = sessionRepository.findFirstRecognizedDate(cycleId)
                .orElseThrow(() -> new IllegalStateException("사이클에 인정 회차가 없습니다. cycleId=" + cycleId));
        LocalDate endDate = sessionRepository.findLastRecognizedDate(cycleId)
                .orElseThrow(() -> new IllegalStateException("사이클에 인정 회차가 없습니다. cycleId=" + cycleId));

        return ReportResponse.of(report,
                settlementReportProgressRepository.findAllByReportIdOrderBySortOrder(report.getId()),
                startDate, endDate);
    }
}
