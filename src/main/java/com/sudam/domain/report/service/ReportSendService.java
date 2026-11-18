package com.sudam.domain.report.service;

import com.sudam.domain.report.dto.response.ReportSendResponse;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.exception.ReportException;
import com.sudam.domain.report.exception.code.ReportErrorCode;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.share.enums.ShareKind;
import com.sudam.domain.share.enums.ShareSourceType;
import com.sudam.domain.share.service.LinkShareRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 정산 리포트 발송 확인 서비스. 프론트가 카카오톡 공유 창을 띄운 뒤 호출한다.
 * 공유 정보 생성(POST /shares)이 남긴 REQUESTED 공유도 여기서 SENT로 바꾼다. 카카오 웹훅은 UT 이후.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportSendService {

    private final SettlementReportRepository settlementReportRepository;
    private final LinkShareRecorder linkShareRecorder;
    private final Clock clock;

    /***
     * 함수 기능: 정산 리포트를 발송 완료(SENT)로 바꾼다. 이미 SENT면 첫 발송 시각을 그대로 돌려준다.
     * @param teacherId 로그인한 선생님 (= users.id)
     * @param reportId  정산 리포트 ID
     * @return 발송 확인 응답
     * @throws ReportException 리포트가 없거나 남의 리포트면 REPORT404_1, 코멘트 확인 전이면 REPORT409_2
     */
    @Transactional
    public ReportSendResponse send(Long teacherId, Long reportId) {
        // 코멘트 저장과 겹치면 늦게 커밋한 쪽이 상태를 덮어쓰므로 리포트 행을 잠근 뒤 바꾼다.
        // 남의 리포트도 '없음'으로 응답한다 (조회 API와 같은 방식).
        SettlementReport report = settlementReportRepository.findByIdForUpdate(reportId)
                .filter(found -> found.getTutoring().getTeacher().getId().equals(teacherId))
                .orElseThrow(() -> new ReportException(ReportErrorCode.REPORT_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        report.markSent(now);
        // 공유 정보를 만들 때 남긴 REQUESTED 공유도 보낸 것으로 바꾼다 (웹훅 전에는 이 확인이 발송 증거다).
        linkShareRecorder.markSentByTeacher(ShareKind.SETTLEMENT_REPORT, ShareSourceType.REPORT, reportId, now);
        return ReportSendResponse.from(report);
    }
}
