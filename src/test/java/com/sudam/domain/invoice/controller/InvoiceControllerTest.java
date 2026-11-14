package com.sudam.domain.invoice.controller;

import com.sudam.domain.invoice.dto.request.ConfirmPaymentRequest;
import com.sudam.domain.invoice.dto.response.InvoiceItemResponse;
import com.sudam.domain.invoice.dto.response.InvoiceListResponse;
import com.sudam.domain.invoice.dto.response.PaymentConfirmResponse;
import com.sudam.domain.invoice.enums.InvoiceStatus;
import com.sudam.domain.invoice.enums.InvoiceStatusLineType;
import com.sudam.domain.invoice.exception.InvoiceException;
import com.sudam.domain.invoice.exception.code.InvoiceErrorCode;
import com.sudam.domain.invoice.service.InvoiceService;
import com.sudam.domain.user.enums.Role;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@Import(InvoiceControllerTest.TestSecurityConfig.class)
@DisplayName("InvoiceController 요청 바인딩 · 검증 · 응답 형식")
class InvoiceControllerTest {

    private static final Long TEACHER_ID = 1L;
    private static final LocalDateTime NOV_15 = LocalDateTime.of(2026, 11, 15, 20, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceService invoiceService;

    @Test
    @DisplayName("month가 YYYY-MM 형식이 아니면 COMMON400이고 result에 month 메시지가 담긴다")
    void 월_형식_오류() throws Exception {
        mockMvc.perform(get("/api/v1/invoices").param("month", "2026-13").with(teacher()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.month").value("월은 YYYY-MM 형식이어야 합니다."));

        verifyNoInteractions(invoiceService);
    }

    @Test
    @DisplayName("입금 현황 조회는 200 · INVOICE200_1과 화면 필드를 돌려주고 month를 YearMonth로 넘긴다")
    void 입금_현황_조회() throws Exception {
        // given
        given(invoiceService.getInvoices(TEACHER_ID, YearMonth.of(2026, 11)))
                .willReturn(InvoiceListResponse.from(List.of(item(InvoiceStatus.PARTIAL, 120_000))));

        // when & then
        mockMvc.perform(get("/api/v1/invoices").param("month", "2026-11").with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("INVOICE200_1"))
                .andExpect(jsonPath("$.result.confirmRequestedCount").value(0))
                .andExpect(jsonPath("$.result.unpaidCount").value(1))
                .andExpect(jsonPath("$.result.summary.totalAmount").value(360_000))
                .andExpect(jsonPath("$.result.summary.paidAmount").value(120_000))
                .andExpect(jsonPath("$.result.summary.unpaidAmount").value(240_000))
                .andExpect(jsonPath("$.result.items[0].invoiceId").value(10))
                .andExpect(jsonPath("$.result.items[0].invoiceName").value("이번 8회차"))
                .andExpect(jsonPath("$.result.items[0].statusLine.type").value("PARTIAL_PAID"))
                .andExpect(jsonPath("$.result.items[0].statusLine.at").value("2026-11-15T20:00:00"));
    }

    @Test
    @DisplayName("month를 비우면 서비스에 null을 넘겨 이번 달로 조회하게 한다")
    void 월_생략() throws Exception {
        // given
        given(invoiceService.getInvoices(TEACHER_ID, null)).willReturn(InvoiceListResponse.from(List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/invoices").with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVOICE200_1"))
                .andExpect(jsonPath("$.result.items").isEmpty());
    }

    @Test
    @DisplayName("amount가 0이면 COMMON400이고 result에 amount 메시지가 담긴다")
    void 금액_0_검증_실패() throws Exception {
        mockMvc.perform(post("/api/v1/invoices/10/payments").with(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.amount").value("입금 금액은 1원 이상이어야 합니다."));

        verifyNoInteractions(invoiceService);
    }

    @Test
    @DisplayName("입금 확인은 201 · INVOICE201_1과 paymentId · appliedAmount · item을 돌려준다")
    void 입금_확인_201() throws Exception {
        // given
        given(invoiceService.confirmPayment(eq(TEACHER_ID), eq(10L), any(ConfirmPaymentRequest.class)))
                .willReturn(new PaymentConfirmResponse(7L, 120_000, item(InvoiceStatus.PARTIAL, 120_000)));

        // when & then
        mockMvc.perform(post("/api/v1/invoices/10/payments").with(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 120000, \"confirmedAt\": \"2026-11-15T20:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("INVOICE201_1"))
                .andExpect(jsonPath("$.message").value("입금이 성공적으로 확인되었습니다."))
                .andExpect(jsonPath("$.result.paymentId").value(7))
                .andExpect(jsonPath("$.result.appliedAmount").value(120_000))
                .andExpect(jsonPath("$.result.item.balance").value(240_000))
                .andExpect(jsonPath("$.result.item.statusLine.type").value("PARTIAL_PAID"));

        verify(invoiceService).confirmPayment(TEACHER_ID, 10L, new ConfirmPaymentRequest(120_000, NOV_15));
    }

    @Test
    @DisplayName("본문 없이 입금 확인하면 금액 · 시각이 빈 요청으로 서비스에 넘긴다")
    void 본문_없는_입금_확인() throws Exception {
        // given
        given(invoiceService.confirmPayment(eq(TEACHER_ID), eq(10L), any(ConfirmPaymentRequest.class)))
                .willReturn(new PaymentConfirmResponse(7L, 360_000, item(InvoiceStatus.PAID, 360_000)));

        // when & then
        mockMvc.perform(post("/api/v1/invoices/10/payments").with(teacher()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("INVOICE201_1"));

        verify(invoiceService).confirmPayment(TEACHER_ID, 10L, ConfirmPaymentRequest.empty());
    }

    @Test
    @DisplayName("서비스가 INVOICE404_1을 던지면 404 · INVOICE404_1 실패 응답이다")
    void 다른_선생님_청구() throws Exception {
        // given
        given(invoiceService.confirmPayment(eq(TEACHER_ID), eq(10L), any(ConfirmPaymentRequest.class)))
                .willThrow(new InvoiceException(InvoiceErrorCode.INVOICE_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/api/v1/invoices/10/payments").with(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INVOICE404_1"));
    }

    private static InvoiceItemResponse item(InvoiceStatus status, int paidAmount) {
        return new InvoiceItemResponse(10L, 100L, "서아", "이번 8회차", 360_000, paidAmount, 360_000 - paidAmount, 0,
                status, new InvoiceItemResponse.StatusLine(
                        status == InvoiceStatus.PAID ? InvoiceStatusLineType.NONE : InvoiceStatusLineType.PARTIAL_PAID,
                        status == InvoiceStatus.PAID ? null : NOV_15),
                null, NOV_15, NOV_15);
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
