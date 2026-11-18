package com.sudam.domain.report.controller;

import com.sudam.domain.participant.exception.ParticipantException;
import com.sudam.domain.participant.exception.code.ParticipantErrorCode;
import com.sudam.domain.report.dto.response.ParentReportResponse;
import com.sudam.domain.report.dto.response.ReportResponse;
import com.sudam.domain.report.enums.ReportStatus;
import com.sudam.domain.report.service.ParentReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParentReportController.class)
@Import(ParentReportControllerTest.TestSecurityConfig.class)
@DisplayName("ParentReportController 학부모 링크 응답 형식")
class ParentReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParentReportService parentReportService;

    @Test
    @DisplayName("로그인 없이 토큰으로 열면 200 · REPORT200_7이고 요약 · 코멘트 · 선생님 이름을 내려준다")
    void 리포트_열기() throws Exception {
        // given
        given(parentReportService.get("mother-token", 21L)).willReturn(new ParentReportResponse(
                new ReportResponse(21L, ReportStatus.SENT, "윤서아",
                        new ReportResponse.Period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
                        null, List.of(), null),
                "이번 달 서아는 …", "한서윤", LocalDateTime.of(2026, 10, 1, 9, 20, 0)));

        // when & then
        mockMvc.perform(get("/api/v1/links/mother-token/reports/21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("REPORT200_7"))
                .andExpect(jsonPath("$.result.report.reportId").value(21))
                .andExpect(jsonPath("$.result.report.studentName").value("윤서아"))
                .andExpect(jsonPath("$.result.comment").value("이번 달 서아는 …"))
                .andExpect(jsonPath("$.result.teacherName").value("한서윤"))
                .andExpect(jsonPath("$.result.sentAt").value("2026-10-01T09:20:00"));
    }

    @Test
    @DisplayName("링크가 올바르지 않으면 404 · PARTICIPANT404_1 실패 응답이다")
    void 잘못된_링크() throws Exception {
        // given
        given(parentReportService.get("expired-token", 21L))
                .willThrow(new ParticipantException(ParticipantErrorCode.LINK_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/links/expired-token/reports/21"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PARTICIPANT404_1"));
    }

    /**
     * 테스트용 보안 설정. 운영 SecurityConfig는 OAuth2 · JWT 빈을 요구해 슬라이스에서 띄울 수 없으므로,
     * 운영과 같이 /api/v1/links/**만 열고 나머지는 인증을 요구한다.
     */
    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/links/**").permitAll()
                            .anyRequest().authenticated())
                    .build();
        }
    }
}
