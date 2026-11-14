package com.sudam.domain.invoice.controller;

import com.sudam.domain.invoice.dto.request.AdjustInvoiceAmountRequest;
import com.sudam.domain.invoice.dto.response.ReportInvoiceResponse;
import com.sudam.domain.invoice.exception.InvoiceException;
import com.sudam.domain.invoice.exception.code.InvoiceErrorCode;
import com.sudam.domain.invoice.service.ReportInvoiceService;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.user.enums.Role;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceAdjustmentController.class)
@Import(InvoiceAdjustmentControllerTest.TestSecurityConfig.class)
@DisplayName("InvoiceAdjustmentController 교육비 수정 요청 검증 · 응답 형식")
class InvoiceAdjustmentControllerTest {

    private static final Long TEACHER_ID = 1L;
    private static final String URL = "/api/v1/invoices/301/adjustments";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportInvoiceService reportInvoiceService;

    static Stream<Arguments> invalidBodies() {
        String reason201 = "가".repeat(201);
        return Stream.of(
                Arguments.of("amount 누락", "{\"reason\": \"교재비\"}", "amount", "교육비를 입력해 주세요."),
                Arguments.of("amount 음수", "{\"amount\": -1, \"reason\": \"교재비\"}", "amount",
                        "교육비는 0원 이상이어야 합니다."),
                Arguments.of("amount 10,000,001", "{\"amount\": 10000001, \"reason\": \"교재비\"}", "amount",
                        "교육비는 10,000,000원 이하여야 합니다."),
                Arguments.of("reason 공백", "{\"amount\": 375000, \"reason\": \"   \"}", "reason",
                        "수정 사유를 입력해 주세요."),
                Arguments.of("reason 201자", "{\"amount\": 375000, \"reason\": \"" + reason201 + "\"}", "reason",
                        "수정 사유는 200자 이내여야 합니다.")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("본문 검증에 실패하면 400 · COMMON400이고 result에 필드별 메시지가 담기며 서비스를 부르지 않는다")
    void 검증_실패(String name, String body, String field, String message) throws Exception {
        mockMvc.perform(post(URL).with(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result." + field).value(message));

        verifyNoInteractions(reportInvoiceService);
    }

    @Test
    @DisplayName("교육비 수정은 200 · INVOICE200_3과 교육비 안내 9개 필드를 돌려준다")
    void 수정_성공() throws Exception {
        // given
        given(reportInvoiceService.adjustAmount(eq(TEACHER_ID), eq(301L), any(AdjustInvoiceAmountRequest.class)))
                .willReturn(new ReportInvoiceResponse(301L, PaymentTiming.PREPAID, 8, LocalDate.of(2026, 9, 14),
                        45_000, 0, 0, 360_000, 375_000));

        // when & then
        mockMvc.perform(post(URL).with(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 375000, \"reason\": \"교재비 15,000원 추가\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("INVOICE200_3"))
                .andExpect(jsonPath("$.message").value("교육비가 성공적으로 수정되었습니다."))
                .andExpect(jsonPath("$.result.invoiceId").value(301))
                .andExpect(jsonPath("$.result.paymentTiming").value("PREPAID"))
                .andExpect(jsonPath("$.result.settlementSessionCnt").value(8))
                .andExpect(jsonPath("$.result.billedCycleStartDate").value("2026-09-14"))
                .andExpect(jsonPath("$.result.unitPrice").value(45_000))
                .andExpect(jsonPath("$.result.canceledCount").value(0))
                .andExpect(jsonPath("$.result.deductionAmount").value(0))
                .andExpect(jsonPath("$.result.calculatedAmount").value(360_000))
                .andExpect(jsonPath("$.result.amount").value(375_000));

        verify(reportInvoiceService).adjustAmount(TEACHER_ID, 301L,
                new AdjustInvoiceAmountRequest(375_000, "교재비 15,000원 추가"));
    }

    @Test
    @DisplayName("서비스가 INVOICE409_4를 던지면 409 · INVOICE409_4 실패 응답이다")
    void 발송한_리포트() throws Exception {
        // given
        given(reportInvoiceService.adjustAmount(eq(TEACHER_ID), eq(301L), any(AdjustInvoiceAmountRequest.class)))
                .willThrow(new InvoiceException(InvoiceErrorCode.REPORT_ALREADY_SENT));

        // when & then
        mockMvc.perform(post(URL).with(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 375000, \"reason\": \"교재비\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INVOICE409_4"));
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
