package com.sudam.domain.report.service;

import com.sudam.domain.report.dto.response.ReportCommentResponse;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.exception.ReportException;
import com.sudam.domain.report.exception.code.ReportErrorCode;
import com.sudam.domain.report.repository.SettlementReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 정산 리포트 코멘트 조회 · 확인 서비스. 리포트를 만든 과외의 선생님만 다룰 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportCommentService {

    private final SettlementReportRepository settlementReportRepository;
    private final Clock clock;

    /***
     * 함수 기능: 정산 리포트의 코멘트 · 출처 · 확인 여부를 조회한다.
     * @param teacherId 로그인한 선생님 (= users.id)
     * @param reportId  정산 리포트 ID
     * @return 코멘트 응답
     * @throws ReportException 리포트가 없거나 다른 선생님 과외의 리포트면 REPORT404_1
     */
    public ReportCommentResponse get(Long teacherId, Long reportId) {
        return ReportCommentResponse.from(ownedOrThrow(settlementReportRepository.findWithTutoringById(reportId), teacherId));
    }

    /***
     * 함수 기능: 선생님이 코멘트를 저장하고 확인한다. 출처는 TEACHER, 확인 시각은 지금이 된다.
     * @param teacherId 로그인한 선생님 (= users.id)
     * @param reportId  정산 리포트 ID
     * @param text      저장할 코멘트
     * @return 저장 후 코멘트 응답
     * @throws ReportException 리포트가 없거나 남의 리포트면 REPORT404_1, 이미 발송했으면 REPORT409_1
     */
    @Transactional
    public ReportCommentResponse save(Long teacherId, Long reportId, String text) {
        // 발송 확인과 겹치면 늦게 커밋한 쪽이 상태를 덮어쓰므로 리포트 행을 잠근 뒤 바꾼다.
        SettlementReport report = ownedOrThrow(settlementReportRepository.findByIdForUpdate(reportId), teacherId);
        report.reviewComment(text, LocalDateTime.now(clock));
        return ReportCommentResponse.from(report);
    }

    // 남의 리포트도 '없음'으로 응답해 리포트 ID가 있는지조차 드러내지 않는다 (조회 API와 같은 방식).
    private SettlementReport ownedOrThrow(Optional<SettlementReport> found, Long teacherId) {
        return found
                .filter(report -> report.getTutoring().getTeacher().getId().equals(teacherId))
                .orElseThrow(() -> new ReportException(ReportErrorCode.REPORT_NOT_FOUND));
    }
}
