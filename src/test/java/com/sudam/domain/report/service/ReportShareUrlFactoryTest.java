package com.sudam.domain.report.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReportShareUrlFactory 학부모 리포트 URL")
class ReportShareUrlFactoryTest {

    @Test
    @DisplayName("템플릿의 {token} · {reportId}를 채운다")
    void URL_만들기() {
        // given
        ReportShareUrlFactory factory = new ReportShareUrlFactory("https://sudam.app/l/{token}/reports/{reportId}");

        // when
        String url = factory.reportUrl("Ab-_9z", 21L);

        // then
        assertThat(url).isEqualTo("https://sudam.app/l/Ab-_9z/reports/21");
    }

    @Test
    @DisplayName("템플릿에 {token}이나 {reportId}가 없으면 기동 단계에서 실패한다")
    void 잘못된_템플릿() {
        // when & then
        assertThatThrownBy(() -> new ReportShareUrlFactory("https://sudam.app/l/{token}"))
                .isInstanceOf(IllegalStateException.class);
    }
}
