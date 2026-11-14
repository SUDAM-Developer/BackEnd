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
import com.sudam.global.idempotency.IdempotencyFingerprint;
import com.sudam.global.idempotency.IdempotencyRecord;
import com.sudam.global.idempotency.InMemoryIdempotencyStore;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@Import(InvoiceControllerTest.TestSecurityConfig.class)
@DisplayName("InvoiceController 요청 바인딩 · 검증 · 응답 형식")
class InvoiceControllerTest {

    private static final Long TEACHER_ID = 1L;
    private static final LocalDateTime NOV_15 = LocalDateTime.of(2026, 11, 15, 20, 0);
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String KEY = "3f1c2a9e-6b7d-4e21-9a0f-1c2d3e4f5a6b";
    private static final String BODY = "{\"amount\": 120000}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryIdempotencyStore idempotencyStore;

    @MockitoBean
    private InvoiceService invoiceService;

    @BeforeEach
    void clearStore() {
        idempotencyStore.clear();
    }

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
        mockMvc.perform(post("/api/v1/invoices/10/payments").with(teacher()).header(IDEMPOTENCY_KEY, KEY)
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
        mockMvc.perform(post("/api/v1/invoices/10/payments").with(teacher()).header(IDEMPOTENCY_KEY, KEY)
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
        mockMvc.perform(post("/api/v1/invoices/10/payments").with(teacher()).header(IDEMPOTENCY_KEY, KEY))
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
        mockMvc.perform(post("/api/v1/invoices/10/payments").with(teacher()).header(IDEMPOTENCY_KEY, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INVOICE404_1"));
    }

    @Test
    @DisplayName("같은 멱등 키로 두 번 보내면 서비스는 한 번만 실행되고 두 번째는 같은 201 응답을 재현한다")
    void 같은_키_두_번() throws Exception {
        // given
        givenPaymentConfirmed();

        // when
        mockMvc.perform(confirm(10L, KEY, BODY))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.result.paymentId").value(7));

        // then
        mockMvc.perform(confirm(10L, KEY, BODY))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("INVOICE201_1"))
                .andExpect(jsonPath("$.result.paymentId").value(7));

        verify(invoiceService, times(1)).confirmPayment(anyLong(), anyLong(), any(ConfirmPaymentRequest.class));
    }

    @Test
    @DisplayName("본문 없이 같은 키로 다시 보내도 저장된 응답을 재현한다")
    void 본문_없는_재요청도_재현() throws Exception {
        // given
        givenPaymentConfirmed();

        // when
        mockMvc.perform(confirm(10L, KEY, null)).andExpect(status().isCreated());

        // then
        mockMvc.perform(confirm(10L, KEY, null))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        verify(invoiceService, times(1)).confirmPayment(anyLong(), anyLong(), any(ConfirmPaymentRequest.class));
    }

    @Test
    @DisplayName("같은 키 · 다른 본문이면 422 · IDEMPOTENCY422_1이고 서비스는 다시 실행되지 않는다")
    void 같은_키_다른_본문() throws Exception {
        // given
        givenPaymentConfirmed();
        mockMvc.perform(confirm(10L, KEY, BODY)).andExpect(status().isCreated());

        // when & then
        mockMvc.perform(confirm(10L, KEY, "{\"amount\": 50000}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY422_1"));

        verify(invoiceService, times(1)).confirmPayment(anyLong(), anyLong(), any(ConfirmPaymentRequest.class));
    }

    @Test
    @DisplayName("같은 키 · 다른 청구 경로면 422 · IDEMPOTENCY422_1이다")
    void 같은_키_다른_경로() throws Exception {
        // given
        givenPaymentConfirmed();
        mockMvc.perform(confirm(10L, KEY, BODY)).andExpect(status().isCreated());

        // when & then
        mockMvc.perform(confirm(11L, KEY, BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY422_1"));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 400 · IDEMPOTENCY400_1이고 서비스를 부르지 않는다")
    void 헤더_없음() throws Exception {
        mockMvc.perform(post("/api/v1/invoices/10/payments").with(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY400_1"));

        verifyNoInteractions(invoiceService);
    }

    @Test
    @DisplayName("Idempotency-Key가 UUID 형식이 아니면 400 · IDEMPOTENCY400_1이다")
    void 헤더_UUID_아님() throws Exception {
        mockMvc.perform(confirm(10L, "abc", BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY400_1"));

        verifyNoInteractions(invoiceService);
    }

    @Test
    @DisplayName("같은 키의 첫 요청이 처리 중이면 409 · IDEMPOTENCY409_1이고 서비스를 부르지 않는다")
    void 처리_중() throws Exception {
        // given
        String fingerprint = IdempotencyFingerprint.of("POST", "/api/v1/invoices/10/payments", null,
                BODY.getBytes(StandardCharsets.UTF_8));
        idempotencyStore.put(TEACHER_ID, KEY, IdempotencyRecord.processing(fingerprint, "first-token"));

        // when & then
        mockMvc.perform(confirm(10L, KEY, BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY409_1"));

        verifyNoInteractions(invoiceService);
    }

    @Test
    @DisplayName("실패 응답은 저장하지 않아 같은 키로 다시 보내면 서비스가 다시 실행된다")
    void 실패_응답은_저장하지_않는다() throws Exception {
        // given
        given(invoiceService.confirmPayment(eq(TEACHER_ID), eq(10L), any(ConfirmPaymentRequest.class)))
                .willThrow(new InvoiceException(InvoiceErrorCode.ALREADY_PAID))
                .willReturn(new PaymentConfirmResponse(7L, 120_000, item(InvoiceStatus.PARTIAL, 120_000)));
        mockMvc.perform(confirm(10L, KEY, BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVOICE409_3"));

        // when & then
        mockMvc.perform(confirm(10L, KEY, BODY))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.result.paymentId").value(7));

        verify(invoiceService, times(2)).confirmPayment(anyLong(), anyLong(), any(ConfirmPaymentRequest.class));
    }

    @Test
    @DisplayName("처리 중 예약이 만료돼 다른 요청이 키를 가져갔으면, 늦게 끝난 첫 요청은 그 예약을 덮어쓰지 않는다")
    void 만료_후_남의_예약은_덮어쓰지_않는다() throws Exception {
        // given
        IdempotencyRecord others = IdempotencyRecord.processing("other-fingerprint", "other-token");
        given(invoiceService.confirmPayment(eq(TEACHER_ID), eq(10L), any(ConfirmPaymentRequest.class)))
                .willAnswer(invocation -> {
                    idempotencyStore.put(TEACHER_ID, KEY, others);
                    return new PaymentConfirmResponse(7L, 120_000, item(InvoiceStatus.PARTIAL, 120_000));
                });

        // when
        mockMvc.perform(confirm(10L, KEY, BODY)).andExpect(status().isCreated());

        // then
        assertThat(idempotencyStore.find(TEACHER_ID, KEY)).contains(others);
    }

    @Test
    @DisplayName("처리 중 예약이 만료돼 다른 요청이 키를 가져갔으면, 첫 요청이 실패해도 그 예약을 지우지 않는다")
    void 만료_후_실패해도_남의_예약은_지우지_않는다() throws Exception {
        // given
        IdempotencyRecord others = IdempotencyRecord.processing("other-fingerprint", "other-token");
        given(invoiceService.confirmPayment(eq(TEACHER_ID), eq(10L), any(ConfirmPaymentRequest.class)))
                .willAnswer(invocation -> {
                    idempotencyStore.put(TEACHER_ID, KEY, others);
                    throw new InvoiceException(InvoiceErrorCode.ALREADY_PAID);
                });

        // when
        mockMvc.perform(confirm(10L, KEY, BODY)).andExpect(status().isConflict());

        // then
        assertThat(idempotencyStore.find(TEACHER_ID, KEY)).contains(others);
    }

    @Test
    @DisplayName("멱등 키가 붙은 본문이 64KB를 넘으면 메모리에 올리지 않고 413 · COMMON413으로 거절한다")
    void 본문_64KB_초과() throws Exception {
        // given
        String bigBody = "{\"amount\": 120000, \"pad\": \"" + "a".repeat(64 * 1024) + "\"}";

        // when & then
        mockMvc.perform(confirm(10L, KEY, bigBody))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON413"));

        verifyNoInteractions(invoiceService);
    }

    @Test
    @DisplayName("@Idempotent가 아닌 조회에 멱등 키 헤더가 붙어도 그대로 처리된다")
    void 조회에_헤더가_붙어도_정상() throws Exception {
        // given
        given(invoiceService.getInvoices(TEACHER_ID, null)).willReturn(InvoiceListResponse.from(List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/invoices").with(teacher()).header(IDEMPOTENCY_KEY, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVOICE200_1"));
    }

    private void givenPaymentConfirmed() {
        given(invoiceService.confirmPayment(eq(TEACHER_ID), anyLong(), any(ConfirmPaymentRequest.class)))
                .willReturn(new PaymentConfirmResponse(7L, 120_000, item(InvoiceStatus.PARTIAL, 120_000)));
    }

    private static MockHttpServletRequestBuilder confirm(Long invoiceId, String key, String body) {
        MockHttpServletRequestBuilder request = post("/api/v1/invoices/{invoiceId}/payments", invoiceId)
                .with(teacher())
                .header(IDEMPOTENCY_KEY, key);
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
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

        // 슬라이스에는 Redis 저장소 빈이 없으므로 같은 계약의 메모리 저장소로 멱등 흐름을 확인한다.
        @Bean
        InMemoryIdempotencyStore idempotencyStore() {
            return new InMemoryIdempotencyStore();
        }
    }
}
