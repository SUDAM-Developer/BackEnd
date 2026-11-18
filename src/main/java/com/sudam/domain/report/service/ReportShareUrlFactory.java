package com.sudam.domain.report.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 학부모가 카카오톡 메시지에서 여는 리포트 링크 화면 URL을 만든다.
 * 화면 경로는 프론트가 정하므로 프로파일별 app.link.report-url-template에 두고 {token} · {reportId}만 채운다.
 */
@Component
public class ReportShareUrlFactory {

    static final String TOKEN_PLACEHOLDER = "{token}";
    static final String REPORT_ID_PLACEHOLDER = "{reportId}";

    private final String template;

    public ReportShareUrlFactory(@Value("${app.link.report-url-template}") String template) {
        if (!template.contains(TOKEN_PLACEHOLDER) || !template.contains(REPORT_ID_PLACEHOLDER)) {
            throw new IllegalStateException("app.link.report-url-template에 {token}과 {reportId}가 모두 있어야 합니다.");
        }
        this.template = template;
    }

    /***
     * 함수 기능: 참여자 링크 토큰과 리포트 ID로 학부모 리포트 화면 URL을 만든다.
     * @param linkToken 참여자 링크 토큰 (URL 안전 문자열이라 인코딩하지 않는다)
     * @param reportId  정산 리포트 ID
     * @return 공유 URL
     */
    public String reportUrl(String linkToken, Long reportId) {
        return template.replace(TOKEN_PLACEHOLDER, linkToken).replace(REPORT_ID_PLACEHOLDER, String.valueOf(reportId));
    }
}
