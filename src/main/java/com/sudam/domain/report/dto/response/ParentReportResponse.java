package com.sudam.domain.report.dto.response;

import com.sudam.domain.invoice.dto.response.ReportInvoiceResponse;

import java.time.LocalDateTime;

/**
 * 학부모가 링크로 여는 정산 리포트. 선생님 조회와 같은 요약 · 진도에 보낸 코멘트와 함께 보낸 교육비 안내를 더한다.
 * 엔티티를 그대로 내보내지 않는다: 교육비는 별도 엔티티(invoices.report_id)이고,
 * 코멘트 출처 · 확인 시각 같은 선생님용 값이 학부모에게 보이면 안 된다.
 *
 * @param report      요약 · 기간 · 진도 · 이해도 수준 (GET /reports/{reportId}와 같은 형식, parents는 빈 목록)
 * @param comment     선생님이 확인하고 보낸 코멘트
 * @param invoice     함께 보낸 교육비 안내 (GET /reports/{reportId}/invoice와 같은 형식, 계좌는 끝 4자리).
 *                    함께 보낸 청구가 없거나 취소됐으면 null
 * @param teacherName 선생님 이름
 * @param sentAt      보낸 시각
 */
public record ParentReportResponse(

        ReportResponse report,
        String comment,
        ReportInvoiceResponse invoice,
        String teacherName,
        LocalDateTime sentAt) {
}
