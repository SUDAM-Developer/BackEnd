package com.sudam.domain.tutoring.controller;

import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.participant.enums.ParticipantRole;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest;
import com.sudam.domain.tutoring.dto.response.TutoringCreateResponse;
import com.sudam.domain.tutoring.enums.PresetType;
import com.sudam.domain.tutoring.service.TutoringCreateService;
import com.sudam.domain.user.enums.Role;
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
import org.springframework.http.HttpStatus;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TutoringController.class)
@Import(TutoringControllerTest.TestSecurityConfig.class)
@DisplayName("TutoringController 요청 바인딩 · 검증 · 응답 형식")
class TutoringControllerTest {

    private static final Long TEACHER_ID = 1L;
    private static final String URL = "/api/v1/tutorings";
    private static final String KEY = "11111111-1111-4111-8111-111111111111";
    private static final String VALID_PARENTS = "[{\"phone\": \"010-1234-5678\"}]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryIdempotencyStore idempotencyStore;

    @MockitoBean
    private TutoringCreateService tutoringCreateService;

    @BeforeEach
    void clearStore() {
        idempotencyStore.clear();
    }

    @Test
    @DisplayName("정상 요청은 201 · TUTORING201_1과 tutoringId · isFirstTutoring · firstInvoice를 돌려준다")
    void 과외_만들기_201() throws Exception {
        // given
        given(tutoringCreateService.create(eq(TEACHER_ID), any(TutoringCreateRequest.class))).willReturn(response());

        // when & then
        mockMvc.perform(request(body(VALID_PARENTS, "110123456789")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("TUTORING201_1"))
                .andExpect(jsonPath("$.result.tutoringId").value(5))
                .andExpect(jsonPath("$.result.isFirstTutoring").value(true))
                .andExpect(jsonPath("$.result.firstInvoice.accountLast4").value("6789"));
    }

    @Test
    @DisplayName("멱등 키 헤더가 없으면 400 · IDEMPOTENCY400_1이고 서비스를 부르지 않는다")
    void 멱등_키_없으면_400() throws Exception {
        mockMvc.perform(post(URL).with(teacher()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(VALID_PARENTS, "110123456789")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY400_1"));

        verifyNoInteractions(tutoringCreateService);
    }

    @Test
    @DisplayName("같은 멱등 키 · 같은 본문으로 두 번 보내면 한 번만 만들고 두 번째는 첫 201 응답을 재현한다")
    void 같은_키_두_번은_한_번만_생성() throws Exception {
        // given
        given(tutoringCreateService.create(eq(TEACHER_ID), any(TutoringCreateRequest.class))).willReturn(response());

        // when
        mockMvc.perform(request(body(VALID_PARENTS, "110123456789"))).andExpect(status().isCreated());

        // then
        mockMvc.perform(request(body(VALID_PARENTS, "110123456789")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("TUTORING201_1"))
                .andExpect(jsonPath("$.result.tutoringId").value(5));

        verify(tutoringCreateService, times(1)).create(eq(TEACHER_ID), any(TutoringCreateRequest.class));
    }

    @Test
    @DisplayName("학부모가 3명이면 COMMON400이고 result에 invite.parents 메시지가 담긴다")
    void 학부모_3명_검증_실패() throws Exception {
        // given
        String threeParents = "[{\"phone\": \"010-1234-5678\"}, {\"phone\": \"010-1234-5679\"}, {\"phone\": \"010-1234-5670\"}]";

        // when & then
        mockMvc.perform(request(body(threeParents, "110123456789")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result['invite.parents']").value("학부모는 1~2명까지 초대할 수 있습니다."));

        verifyNoInteractions(tutoringCreateService);
    }

    @Test
    @DisplayName("계좌번호가 9자리면 COMMON400이고 account.accountNumber 메시지가 담긴다")
    void 계좌번호_9자리_검증_실패() throws Exception {
        mockMvc.perform(request(body(VALID_PARENTS, "123456789")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result['account.accountNumber']").value("계좌번호를 확인해 주세요."));

        verifyNoInteractions(tutoringCreateService);
    }

    @Test
    @DisplayName("학부모 번호가 011로 시작하면 COMMON400이고 학부모 번호 메시지가 담긴다")
    void 학부모_번호_011_검증_실패() throws Exception {
        mockMvc.perform(request(body("[{\"phone\": \"011-1234-5678\"}]", "110123456789")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result['invite.parents[0].phone']").value("학부모 번호를 확인해 주세요."));

        verifyNoInteractions(tutoringCreateService);
    }

    @Test
    @DisplayName("인증 없이 요청하면 401이다")
    void 미인증_401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body(VALID_PARENTS, "110123456789")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tutoringCreateService);
    }

    private static MockHttpServletRequestBuilder request(String json) {
        return post(URL).with(teacher()).header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static String body(String parentsJson, String accountNumber) {
        return """
                {
                  "preset": {"skipped": true},
                  "student": {"name": "Seoa", "gradeLevel": 3, "subjects": ["MATH"]},
                  "textbooks": [{"name": "Book", "progressUnit": "PAGE"}],
                  "schedules": [{"dayOfWeek": 1, "startTime": "16:00", "endTime": "17:00"}],
                  "policy": {"settlementSessionCount": 8, "paymentTiming": "PREPAID", "tuitionFee": 360000},
                  "account": {"bankName": "Bank", "accountNumber": "%s", "accountHolder": "Kim"},
                  "settings": {"parentUpdateFrequency": "EVERY_SESSION", "homeworkNotifyEnabled": true},
                  "invite": {"parents": %s}
                }
                """.formatted(accountNumber, parentsJson);
    }

    private static TutoringCreateResponse response() {
        return new TutoringCreateResponse(5L, 6L, PresetType.SIMPLE, true,
                new TutoringCreateResponse.FirstLesson(LocalDate.of(2026, 11, 30), 1, LocalTime.of(16, 0)),
                List.of(new TutoringCreateResponse.ParticipantItem(31L, ParticipantRole.PARENT,
                        ParticipantLabel.GUARDIAN, "https://sudam.app/p/x")),
                false,
                new TutoringCreateResponse.FirstInvoice(10L, "첫 8회차", 360_000, "088", "Bank", "6789", "Kim"),
                false);
    }

    // 운영 principal과 같은 CustomOAuth2User(userId, Role)를 SecurityContext에 넣는다 (security-convention.md).
    private static RequestPostProcessor teacher() {
        CustomOAuth2User principal = new CustomOAuth2User(TEACHER_ID, Role.TEACHER);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** 테스트용 보안 설정. 같은 규칙(csrf 끔 · STATELESS · 인증 필요)만 남기고 미인증은 401로 돌려준다. */
    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(e -> e.authenticationEntryPoint(
                            (req, res, ex) -> res.setStatus(HttpStatus.UNAUTHORIZED.value())))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }

        // 멱등 인터셉터가 WebMvcConfig에 등록돼 있어 슬라이스에도 저장소 빈이 필요하다.
        @Bean
        InMemoryIdempotencyStore idempotencyStore() {
            return new InMemoryIdempotencyStore();
        }
    }
}
