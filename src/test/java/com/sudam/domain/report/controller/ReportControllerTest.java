package com.sudam.domain.report.controller;

import com.sudam.domain.report.dto.response.ReportCommentResponse;
import com.sudam.domain.report.dto.response.ReportSendResponse;
import com.sudam.domain.report.enums.AiStatus;
import com.sudam.domain.report.enums.CommentSource;
import com.sudam.domain.report.enums.ReportStatus;
import com.sudam.domain.report.exception.ReportException;
import com.sudam.domain.report.exception.code.ReportErrorCode;
import com.sudam.domain.report.service.ReportCommentService;
import com.sudam.domain.report.service.ReportSendService;
import com.sudam.domain.report.service.ReportService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import(ReportControllerTest.TestSecurityConfig.class)
@DisplayName("ReportController 코멘트 · 발송 응답 형식")
class ReportControllerTest {

    private static final Long TEACHER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private ReportService reportService;
    @MockitoBean private ReportCommentService reportCommentService;
    @MockitoBean private ReportSendService reportSendService;

    @Test
    @DisplayName("코멘트 조회는 200 · REPORT200_2이고 AI 필드는 비워서 내려간다")
    void 코멘트_조회() throws Exception {
        // given
        given(reportCommentService.get(TEACHER_ID, 21L)).willReturn(new ReportCommentResponse(
                "9월에는 장문 독해에서 큰 변화가 있었습니다.", CommentSource.AI_DRAFT, false, AiStatus.DONE, List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/reports/21/comment").with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("REPORT200_2"))
                .andExpect(jsonPath("$.result.comment").value("9월에는 장문 독해에서 큰 변화가 있었습니다."))
                .andExpect(jsonPath("$.result.commentSource").value("AI_DRAFT"))
                .andExpect(jsonPath("$.result.reviewed").value(false))
                .andExpect(jsonPath("$.result.aiStatus").value("DONE"))
                .andExpect(jsonPath("$.result.softenedSentences").isEmpty());
    }

    @Test
    @DisplayName("코멘트 저장은 text를 받아 200 · REPORT200_3으로 확인된 코멘트를 내려준다")
    void 코멘트_저장() throws Exception {
        // given
        given(reportCommentService.save(TEACHER_ID, 21L, "선생님이 고친 코멘트")).willReturn(new ReportCommentResponse(
                "선생님이 고친 코멘트", CommentSource.TEACHER, true, null, List.of()));

        // when & then
        mockMvc.perform(put("/api/v1/reports/21/comment").with(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"선생님이 고친 코멘트\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPORT200_3"))
                .andExpect(jsonPath("$.result.commentSource").value("TEACHER"))
                .andExpect(jsonPath("$.result.reviewed").value(true));
    }

    @Test
    @DisplayName("빈 코멘트를 저장하면 400 · COMMON400이고 result에 text 메시지가 담긴다")
    void 빈_코멘트_저장() throws Exception {
        // when & then
        mockMvc.perform(put("/api/v1/reports/21/comment").with(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.text").value("코멘트를 입력해 주세요."));
        then(reportCommentService).should(never()).save(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("1000자를 넘는 코멘트를 저장하면 400 · COMMON400이다")
    void 긴_코멘트_저장() throws Exception {
        // given
        String body = "{\"text\": \"" + "가".repeat(1001) + "\"}";

        // when & then
        mockMvc.perform(put("/api/v1/reports/21/comment").with(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.text").value("코멘트는 1000자 이하로 입력해 주세요."));
    }

    @Test
    @DisplayName("발송 확인은 200 · REPORT200_4이고 SENT · 발송 시각을 내려준다")
    void 발송() throws Exception {
        // given
        given(reportSendService.send(TEACHER_ID, 21L))
                .willReturn(new ReportSendResponse(21L, ReportStatus.SENT, LocalDateTime.of(2026, 10, 1, 9, 20, 0)));

        // when & then
        mockMvc.perform(post("/api/v1/reports/21/send").with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPORT200_4"))
                .andExpect(jsonPath("$.result.reportId").value(21))
                .andExpect(jsonPath("$.result.status").value("SENT"))
                .andExpect(jsonPath("$.result.sentAt").value("2026-10-01T09:20:00"));
    }

    @Test
    @DisplayName("코멘트 확인 전에 발송하면 409 · REPORT409_2 실패 응답이다")
    void 미확인_발송() throws Exception {
        // given
        given(reportSendService.send(TEACHER_ID, 21L))
                .willThrow(new ReportException(ReportErrorCode.COMMENT_NOT_REVIEWED));

        // when & then
        mockMvc.perform(post("/api/v1/reports/21/send").with(teacher()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("REPORT409_2"));
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
