package com.sudam.domain.schedule.controller;

import com.sudam.domain.schedule.dto.response.DailyScheduleResponse;
import com.sudam.domain.schedule.dto.response.MonthlyScheduleResponse;
import com.sudam.domain.schedule.enums.LessonAction;
import com.sudam.domain.schedule.exception.ScheduleException;
import com.sudam.domain.schedule.exception.code.ScheduleErrorCode;
import com.sudam.domain.schedule.service.ScheduleService;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScheduleController.class)
@Import(ScheduleControllerTest.TestSecurityConfig.class)
@DisplayName("ScheduleController 요청 바인딩 · 응답 형식")
class ScheduleControllerTest {

    private static final Long TEACHER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private ScheduleService scheduleService;

    @Test
    @DisplayName("한 달 달력은 yearMonth를 받아 200 · SCHEDULE200_1로 날짜별 수업 점을 내려준다")
    void 한_달_달력_조회() throws Exception {
        // given
        YearMonth november = YearMonth.of(2026, 11);
        given(scheduleService.monthly(TEACHER_ID, november)).willReturn(new MonthlyScheduleResponse(november, TODAY,
                List.of(new MonthlyScheduleResponse.Student(31L, "김서아")),
                List.of(new MonthlyScheduleResponse.Day(TODAY,
                        List.of(new MonthlyScheduleResponse.Lesson(31L, true, false)), 1, List.of()))));

        // when & then
        mockMvc.perform(get("/api/v1/schedules/monthly").param("yearMonth", "2026-11").with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SCHEDULE200_1"))
                .andExpect(jsonPath("$.result.yearMonth").value("2026-11"))
                .andExpect(jsonPath("$.result.today").value("2026-11-11"))
                .andExpect(jsonPath("$.result.students[0].studentName").value("김서아"))
                .andExpect(jsonPath("$.result.days[0].date").value("2026-11-11"))
                .andExpect(jsonPath("$.result.days[0].lessons[0].canceled").value(true))
                .andExpect(jsonPath("$.result.days[0].lessonCount").value(1))
                .andExpect(jsonPath("$.result.days[0].eventCategories").isEmpty());
    }

    @Test
    @DisplayName("yearMonth 형식이 틀리면 400 · COMMON400이고 result에 yearMonth 메시지가 담긴다")
    void 잘못된_yearMonth() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/schedules/monthly").param("yearMonth", "2026-13").with(teacher()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.yearMonth").exists());
        then(scheduleService).should(never()).monthly(anyLong(), any());
    }

    @Test
    @DisplayName("yearMonth가 없으면 400 · COMMON400이다")
    void yearMonth_누락() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/schedules/monthly").with(teacher()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @Test
    @DisplayName("그날 목록은 200 · SCHEDULE200_2이고 시각은 HH:mm, isToday · isPredicted 키를 그대로 내려준다")
    void 그날_목록_조회() throws Exception {
        // given
        DailyScheduleResponse.Lesson lesson = new DailyScheduleResponse.Lesson(null, 9L, TODAY, TODAY,
                LocalTime.of(20, 30), LocalTime.of(22, 0), 35L, "이도윤", "영어", SessionType.REGULAR,
                SessionStatus.SCHEDULED, null, false, false, false, null,
                new DailyScheduleResponse.Cycle(1, 3, 2, 4, false, true), LessonAction.RECORD);
        given(scheduleService.daily(TEACHER_ID, TODAY, 35L)).willReturn(
                new DailyScheduleResponse(TODAY, true, List.of(), List.of(lesson), List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/schedules/daily").param("date", "2026-11-11").param("tutoringId", "35")
                        .with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SCHEDULE200_2"))
                .andExpect(jsonPath("$.result.isToday").value(true))
                .andExpect(jsonPath("$.result.events").isEmpty())
                .andExpect(jsonPath("$.result.sessions[0].sessionId").doesNotExist())
                .andExpect(jsonPath("$.result.sessions[0].scheduleId").value(9))
                .andExpect(jsonPath("$.result.sessions[0].startTime").value("20:30"))
                .andExpect(jsonPath("$.result.sessions[0].endTime").value("22:00"))
                .andExpect(jsonPath("$.result.sessions[0].subjectName").value("영어"))
                .andExpect(jsonPath("$.result.sessions[0].cycle.noInCycle").value(3))
                .andExpect(jsonPath("$.result.sessions[0].cycle.isPredicted").value(true))
                .andExpect(jsonPath("$.result.sessions[0].action").value("RECORD"));
    }

    @Test
    @DisplayName("date 없이 부르면 서비스에 null을 넘겨 오늘로 조회한다")
    void date_없이_조회() throws Exception {
        // given
        given(scheduleService.daily(TEACHER_ID, null, null)).willReturn(
                new DailyScheduleResponse(TODAY, true, List.of(), List.of(), List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/schedules/daily").with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.date").value("2026-11-11"))
                .andExpect(jsonPath("$.result.sessions").isEmpty());
    }

    @Test
    @DisplayName("date 형식이 틀리면 400 · COMMON400이다")
    void 잘못된_date() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/schedules/daily").param("date", "2026/11/11").with(teacher()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.date").exists());
    }

    @Test
    @DisplayName("내 과외가 아닌 tutoringId면 404 · SCHEDULE404_1이다")
    void 내_과외가_아니면_404() throws Exception {
        // given
        given(scheduleService.daily(TEACHER_ID, null, 999L))
                .willThrow(new ScheduleException(ScheduleErrorCode.TUTORING_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/schedules/daily").param("tutoringId", "999").with(teacher()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("SCHEDULE404_1"));
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
