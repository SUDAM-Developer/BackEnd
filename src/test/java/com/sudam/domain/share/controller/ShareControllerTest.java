package com.sudam.domain.share.controller;

import com.sudam.domain.share.dto.request.CreateShareRequest;
import com.sudam.domain.share.dto.response.ShareResponse;
import com.sudam.domain.share.enums.ShareKind;
import com.sudam.domain.share.exception.ShareException;
import com.sudam.domain.share.exception.code.ShareErrorCode;
import com.sudam.domain.share.service.ShareService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShareController.class)
@Import(ShareControllerTest.TestSecurityConfig.class)
@DisplayName("ShareController 요청 검증 · 응답 형식")
class ShareControllerTest {

    private static final Long TEACHER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShareService shareService;

    @Test
    @DisplayName("공유 정보 생성은 201 · SHARE201_1이고 URL · 제목 · 설명 · 버튼 문구를 내려준다")
    void 공유_정보_생성() throws Exception {
        // given
        given(shareService.create(TEACHER_ID, new CreateShareRequest(ShareKind.SETTLEMENT_REPORT, 21L, 12L)))
                .willReturn(new ShareResponse(501L, "https://sudam.app/l/Xf3k/reports/21",
                        "윤서아 학생 8회차 정산 리포트", "수업 8/8회 · 숙제 수행률 88% · 다음 8회차 교육비 360,000원",
                        "링크로 보기"));

        // when & then
        mockMvc.perform(post("/api/v1/shares").with(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"SETTLEMENT_REPORT\", \"sourceId\": 21, \"participantId\": 12}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SHARE201_1"))
                .andExpect(jsonPath("$.result.shareId").value(501))
                .andExpect(jsonPath("$.result.url").value("https://sudam.app/l/Xf3k/reports/21"))
                .andExpect(jsonPath("$.result.title").value("윤서아 학생 8회차 정산 리포트"))
                .andExpect(jsonPath("$.result.buttonTitle").value("링크로 보기"));
    }

    @Test
    @DisplayName("kind · participantId가 없으면 400 · COMMON400이고 result에 필드별 메시지가 담긴다")
    void 필수값_누락() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/shares").with(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\": 21}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.kind").value("공유 종류를 골라 주세요."))
                .andExpect(jsonPath("$.result.participantId").value("받는 사람을 골라 주세요."));
        then(shareService).should(never()).create(any(), any());
    }

    @Test
    @DisplayName("수신을 거부한 받는 사람이면 409 · SHARE409_1 실패 응답이다")
    void 수신_거부() throws Exception {
        // given
        given(shareService.create(TEACHER_ID, new CreateShareRequest(ShareKind.PAYMENT_RESEND, 12L, 31L)))
                .willThrow(new ShareException(ShareErrorCode.RECIPIENT_REJECTED));

        // when & then
        mockMvc.perform(post("/api/v1/shares").with(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"PAYMENT_RESEND\", \"sourceId\": 12, \"participantId\": 31}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("SHARE409_1"));
    }

    // 운영 principal과 같은 CustomOAuth2User(userId, Role)를 SecurityContext에 넣는다 (security-convention.md).
    private static RequestPostProcessor teacher() {
        CustomOAuth2User principal = new CustomOAuth2User(TEACHER_ID, Role.TEACHER);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /**
     * 테스트용 보안 설정. 운영 SecurityConfig는 OAuth2 · JWT 빈을 요구해 슬라이스에서 띄울 수 없으므로
     * 같은 규칙(csrf 끔 · STATELESS · 인증 필요)만 남긴다.
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
