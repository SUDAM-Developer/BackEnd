package com.sudam.domain.share.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ShareLinkFactory 링크 화면 URL")
class ShareLinkFactoryTest {

    @Test
    @DisplayName("리포트 · 교육비 화면은 /l/{token} 뒤에 화면 경로를 붙인다")
    void 화면별_URL() {
        // given
        ShareLinkFactory factory = new ShareLinkFactory("https://sudam.app");

        // when & then
        assertThat(factory.reportUrl("Ab-_9z", 21L)).isEqualTo("https://sudam.app/l/Ab-_9z/reports/21");
        assertThat(factory.invoiceUrl("Ab-_9z", 12L)).isEqualTo("https://sudam.app/l/Ab-_9z/invoices/12");
    }

    @Test
    @DisplayName("주소 끝에 '/'가 있어도 '//'가 생기지 않는다")
    void 끝_슬래시() {
        // given
        ShareLinkFactory factory = new ShareLinkFactory("https://sudam.app/");

        // when & then
        assertThat(factory.reportUrl("t", 1L)).isEqualTo("https://sudam.app/l/t/reports/1");
    }
}
