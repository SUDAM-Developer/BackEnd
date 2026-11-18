package com.sudam.domain.report.dto.response;

import java.time.LocalDateTime;

/**
 * 학부모가 링크로 여는 정산 리포트. 선생님 조회와 같은 요약 · 진도에 보낸 코멘트를 더한다.
 * 교육비 안내(계좌 등)는 아직 담지 않는다.
 *
 * @param report      요약 · 기간 · 진도 · 이해도 수준 (GET /reports/{reportId}와 같은 형식)
 * @param comment     선생님이 확인하고 보낸 코멘트
 * @param teacherName 선생님 이름
 * @param sentAt      보낸 시각
 */
public record ParentReportResponse(ReportResponse report, String comment, String teacherName, LocalDateTime sentAt) {
}
