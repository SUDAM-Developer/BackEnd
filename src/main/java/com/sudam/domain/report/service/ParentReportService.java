package com.sudam.domain.report.service;

import com.sudam.domain.invoice.dto.response.ReportInvoiceResponse;
import com.sudam.domain.invoice.repository.InvoiceRepository;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.exception.ParticipantException;
import com.sudam.domain.participant.service.ParticipantLinkService;
import com.sudam.domain.report.dto.response.ParentReportResponse;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.enums.ReportStatus;
import com.sudam.domain.report.exception.ReportException;
import com.sudam.domain.report.exception.code.ReportErrorCode;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.report.repository.SettlementReportViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 학부모가 카카오톡 링크로 정산 리포트를 연다. 로그인 대신 참여자 링크 토큰으로 본인을 확인하고,
 * 처음 열면 열람을 기록해 선생님 리포트 목록에 '학부모 열람'이 뜨게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParentReportService {

    private final ParticipantLinkService participantLinkService;
    private final SettlementReportRepository settlementReportRepository;
    private final SettlementReportViewRepository settlementReportViewRepository;
    private final InvoiceRepository invoiceRepository;
    private final ReportService reportService;
    private final Clock clock;

    /***
     * 함수 기능: 링크 토큰의 학부모가 자기 과외의 보낸 리포트를 연다. 첫 열람이면 열람 시각을 남긴다.
     * @param linkToken 학부모 링크 토큰
     * @param reportId  정산 리포트 ID
     * @return 요약 · 진도 · 코멘트 · 교육비 안내
     * @throws ParticipantException 링크가 없거나 만료 · 폐기됐거나 학부모가 아니면 PARTICIPANT404_1
     * @throws ReportException      리포트가 없거나, 링크의 과외 리포트가 아니거나, 아직 보내지 않았으면 REPORT404_1
     */
    @Transactional
    public ParentReportResponse get(String linkToken, Long reportId) {
        Participant parent = participantLinkService.resolveParent(linkToken);

        // 보내기 전 리포트는 선생님이 코멘트를 확인하기 전일 수 있으므로 학부모에게는 '없음'으로 보인다.
        SettlementReport report = settlementReportRepository.findWithTutoringById(reportId)
                .filter(found -> found.getTutoring().getId().equals(parent.getTutoring().getId()))
                .filter(found -> found.getStatus() == ReportStatus.SENT)
                .orElseThrow(() -> new ReportException(ReportErrorCode.REPORT_NOT_FOUND));

        settlementReportViewRepository.insertFirstView(reportId, parent.getId(), LocalDateTime.now(clock));

        // 함께 보낸 교육비 안내(취소되지 않은 청구). 리포트 화면이라 계좌는 끝 4자리만 담는다 (ERD: 전체 번호는 교육비 화면만).
        Long teacherId = parent.getTutoring().getTeacher().getId();
        ReportInvoiceResponse invoice = invoiceRepository.findActiveByReportIdAndTeacherId(reportId, teacherId)
                .map(ReportInvoiceResponse::of)
                .orElse(null);

        // 받는 사람 목록은 선생님 화면용이라 학부모에게는 내려주지 않는다.
        return new ParentReportResponse(reportService.toResponse(report, List.of()), report.getComment(), invoice,
                parent.getTutoring().getTeacher().getName(), report.getSentAt());
    }
}
