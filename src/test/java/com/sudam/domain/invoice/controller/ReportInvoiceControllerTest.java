package com.sudam.domain.invoice.controller;

import com.sudam.domain.invoice.dto.response.ReportInvoiceResponse;
import com.sudam.domain.invoice.exception.InvoiceException;
import com.sudam.domain.invoice.exception.code.InvoiceErrorCode;
import com.sudam.domain.invoice.service.ReportInvoiceService;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.user.enums.Role;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportInvoiceController.class)
@Import(ReportInvoiceControllerTest.TestSecurityConfig.class)
@DisplayName("ReportInvoiceController 교육비 안내 조회 응답 형식")
class ReportInvoiceControllerTest {

    private static final Long TEACHER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportInvoiceService reportInvoiceService;

    @Test
    @DisplayName("교육비 안내 조회는 200 · INVOICE200_2이고 FIRST 청구면 billedCycleStartDate가 null이다")
    void 조회_성공() throws Exception {
        // given
        given(reportInvoiceService.getReportInvoice(TEACHER_ID, 21L))
                .willReturn(new ReportInvoiceResponse(301L, PaymentTiming.POSTPAID, 8, null,
                        45_000, 2, 135_000, 225_000, 225_000));

        // when & then
        mockMvc.perform(get("/api/v1/reports/21/invoice").with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("INVOICE200_2"))
                .andExpect(jsonPath("$.message").value("교육비 안내가 성공적으로 조회되었습니다."))
                .andExpect(jsonPath("$.result.invoiceId").value(301))
                .andExpect(jsonPath("$.result.billedCycleStartDate").doesNotExist())
                .andExpect(jsonPath("$.result.canceledCount").value(2))
                .andExpect(jsonPath("$.result.deductionAmount").value(135_000));
    }

    @Test
    @DisplayName("서비스가 INVOICE404_1을 던지면 404 · INVOICE404_1 실패 응답이다")
    void 남의_리포트() throws Exception {
        // given
        given(reportInvoiceService.getReportInvoice(TEACHER_ID, 21L))
                .willThrow(new InvoiceException(InvoiceErrorCode.INVOICE_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/reports/21/invoice").with(teacher()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INVOICE404_1"));
    }

    // 운영 principal과 같은 CustomOAuth2User(userId, Role)를 SecurityContext에 넣는다 (security-convention.md).
    private static RequestPostProcessor teacher() {
        CustomOAuth2User principal = new CustomOAuth2User(TEACHER_ID, Role.TEACHER);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /**
     * 테스트용 보안 설정. 운영 SecurityConfig는 OAuth2 · JWT 빈(Redis 토큰 저장소 등)을 요구해 슬라이스에서 띄울 수 없으므로,
     * 같은 규칙(csrf 끔 · STATELESS · 인증 필요)만 남기고 인증 주체는 요청마다 위 teacher()로 넣는다.
     */
    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }
    }
}
