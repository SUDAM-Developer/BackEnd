package com.sudam.domain.session.controller;

import com.sudam.domain.session.dto.response.TodaySessionsResponse;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;
import com.sudam.domain.session.service.TodaySessionService;
import com.sudam.domain.user.enums.Role;
import com.sudam.domain.user.enums.Subject;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
@Import(SessionControllerTest.TestSecurityConfig.class)
@DisplayName("SessionController 응답 형식")
class SessionControllerTest {

    private static final Long TEACHER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private TodaySessionService todaySessionService;

    @Test
    @DisplayName("오늘 수업은 200 · SESSION200_1이고 날짜 · 시각 형식, isTimeChanged 키, null sessionId를 명세대로 내려준다")
    void 오늘_수업_조회() throws Exception {
        // given
        TodaySessionsResponse.Lesson lesson = TodaySessionsResponse.Lesson.builder()
                .sessionId(null)
                .scheduleId(31L)
                .originalDate(TODAY)
                .tutoringId(7L)
                .studentName("윤서아")
                .gradeLevel(10)
                .gradeOther(null)
                .subjects(List.of(
                        new TodaySessionsResponse.LessonSubject(Subject.ENGLISH, "영어"),
                        new TodaySessionsResponse.LessonSubject(Subject.OTHER, "논술")))
                .sessionType(SessionType.REGULAR)
                .status(SessionStatus.SCHEDULED)
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(19, 30))
                .isTimeChanged(false)
                .regularSchedules(List.of(
                        new TodaySessionsResponse.RegularSchedule(1, LocalTime.of(18, 0), LocalTime.of(19, 30))))
                .cycle(new TodaySessionsResponse.Cycle(5, 4, 5))
                .build();
        given(todaySessionService.today(TEACHER_ID)).willReturn(new TodaySessionsResponse(TODAY, List.of(lesson)));

        // when & then
        mockMvc.perform(get("/api/v1/sessions/today").with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SESSION200_1"))
                .andExpect(jsonPath("$.result.date").value("2026-10-01"))
                .andExpect(jsonPath("$.result.sessions[0].sessionId").isEmpty())
                .andExpect(jsonPath("$.result.sessions[0].scheduleId").value(31))
                .andExpect(jsonPath("$.result.sessions[0].originalDate").value("2026-10-01"))
                .andExpect(jsonPath("$.result.sessions[0].gradeOther").isEmpty())
                .andExpect(jsonPath("$.result.sessions[0].subjects[1].subjectCode").value("OTHER"))
                .andExpect(jsonPath("$.result.sessions[0].subjects[1].subjectName").value("논술"))
                .andExpect(jsonPath("$.result.sessions[0].startTime").value("18:00"))
                .andExpect(jsonPath("$.result.sessions[0].endTime").value("19:30"))
                .andExpect(jsonPath("$.result.sessions[0].isTimeChanged").value(false))
                .andExpect(jsonPath("$.result.sessions[0].timeChanged").doesNotExist())
                .andExpect(jsonPath("$.result.sessions[0].regularSchedules[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.result.sessions[0].regularSchedules[0].startTime").value("18:00"))
                .andExpect(jsonPath("$.result.sessions[0].cycle.sessionNo").value(5))
                .andExpect(jsonPath("$.result.sessions[0].cycle.recordedCount").value(4))
                .andExpect(jsonPath("$.result.sessions[0].cycle.settlementSessionCount").value(5));
    }

    @Test
    @DisplayName("오늘 수업이 없으면 sessions는 빈 배열이다")
    void 오늘_수업_없음() throws Exception {
        // given
        given(todaySessionService.today(TEACHER_ID)).willReturn(new TodaySessionsResponse(TODAY, List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/sessions/today").with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SESSION200_1"))
                .andExpect(jsonPath("$.result.sessions").isArray())
                .andExpect(jsonPath("$.result.sessions").isEmpty());
    }

    private static RequestPostProcessor teacher() {
        CustomOAuth2User principal = new CustomOAuth2User(TEACHER_ID, Role.TEACHER);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

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
