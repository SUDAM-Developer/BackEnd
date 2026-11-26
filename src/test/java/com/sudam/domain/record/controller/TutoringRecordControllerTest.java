package com.sudam.domain.record.controller;

import com.sudam.domain.record.dto.response.RecordFormResponse;
import com.sudam.domain.record.dto.response.SaveRecordResponse;
import com.sudam.domain.record.exception.RecordException;
import com.sudam.domain.record.exception.code.RecordErrorCode;
import com.sudam.domain.record.service.RecordFormService;
import com.sudam.domain.record.service.TutoringRecordService;
import com.sudam.domain.session.enums.SessionType;
import com.sudam.domain.settlement.dto.SettlementResult;
import com.sudam.domain.tutoring.enums.ProgressUnit;
import com.sudam.domain.user.enums.Role;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TutoringRecordController.class)
@Import(TutoringRecordControllerTest.TestSecurityConfig.class)
@DisplayName("TutoringRecordController 요청 바인딩 · 응답 형식")
class TutoringRecordControllerTest {

    private static final Long TEACHER_ID = 1L;
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 30);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private RecordFormService recordFormService;
    @MockitoBean private TutoringRecordService tutoringRecordService;

    @Test
    @DisplayName("기록 화면은 200 · RECORD200_1이고 시각은 HH:mm, sessionId · record 키는 null로 내려준다")
    void 기록_화면_조회() throws Exception {
        // given
        RecordFormResponse response = new RecordFormResponse(null, 31L, WEDNESDAY, WEDNESDAY,
                LocalTime.of(18, 0), LocalTime.of(19, 30), SessionType.REGULAR, "윤서아", 8,
                new RecordFormResponse.CycleSummary(1, 7, 8),
                List.of(new RecordFormResponse.TextbookItem(11L, "수능특강 영어", ProgressUnit.CUSTOM, "강", 6)),
                "6강 예습", null);
        given(recordFormService.getRecordForm(TEACHER_ID, 10L, 31L, WEDNESDAY)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/tutorings/10/record-form")
                        .param("scheduleId", "31").param("originalDate", "2026-09-30").with(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("RECORD200_1"))
                .andExpect(jsonPath("$.result.sessionId").isEmpty())
                .andExpect(jsonPath("$.result.scheduleId").value(31))
                .andExpect(jsonPath("$.result.originalDate").value("2026-09-30"))
                .andExpect(jsonPath("$.result.sessionDate").value("2026-09-30"))
                .andExpect(jsonPath("$.result.startTime").value("18:00"))
                .andExpect(jsonPath("$.result.endTime").value("19:30"))
                .andExpect(jsonPath("$.result.sessionType").value("REGULAR"))
                .andExpect(jsonPath("$.result.studentName").value("윤서아"))
                .andExpect(jsonPath("$.result.noInCycle").value(8))
                .andExpect(jsonPath("$.result.cycle.cycleNo").value(1))
                .andExpect(jsonPath("$.result.cycle.recognizedCount").value(7))
                .andExpect(jsonPath("$.result.cycle.settlementSessionCount").value(8))
                .andExpect(jsonPath("$.result.textbooks[0].textbookId").value(11))
                .andExpect(jsonPath("$.result.textbooks[0].progressUnit").value("CUSTOM"))
                .andExpect(jsonPath("$.result.textbooks[0].customUnitLabel").value("강"))
                .andExpect(jsonPath("$.result.textbooks[0].lastPosition").value(6))
                .andExpect(jsonPath("$.result.previousHomework").value("6강 예습"))
                // 응답 키를 2단계 전부터 고정하므로, 값이 null이어도 키는 있어야 한다.
                .andExpect(jsonPath("$.result.record").hasJsonPath())
                .andExpect(jsonPath("$.result.record").isEmpty());
    }

    @ParameterizedTest(name = "{0}만 보내면 400")
    @CsvSource({"scheduleId, 31", "originalDate, 2026-09-30"})
    @DisplayName("scheduleId · originalDate 중 하나라도 없으면 400 · COMMON400이고 서비스를 부르지 않는다")
    void 파라미터_누락(String name, String value) throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/tutorings/10/record-form").param(name, value).with(teacher()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"));
        then(recordFormService).should(never()).getRecordForm(anyLong(), anyLong(), anyLong(), any());
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({"originalDate, 2026-9-30", "scheduleId, abc"})
    @DisplayName("날짜 형식이나 ID 타입이 틀리면 400 · COMMON400이고 result에 그 파라미터 메시지가 담긴다")
    void 파라미터_형식_오류(String name, String value) throws Exception {
        // given
        String scheduleId = name.equals("scheduleId") ? value : "31";
        String originalDate = name.equals("originalDate") ? value : "2026-09-30";

        // when & then
        mockMvc.perform(get("/api/v1/tutorings/10/record-form")
                        .param("scheduleId", scheduleId).param("originalDate", originalDate).with(teacher()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result." + name).exists());
        then(recordFormService).should(never()).getRecordForm(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("이미 저장된 회차면 409 · RECORD409_1이다")
    void 저장된_회차() throws Exception {
        // given
        given(recordFormService.getRecordForm(TEACHER_ID, 10L, 31L, WEDNESDAY))
                .willThrow(new RecordException(RecordErrorCode.ALREADY_RECORDED));

        // when & then
        mockMvc.perform(get("/api/v1/tutorings/10/record-form")
                        .param("scheduleId", "31").param("originalDate", "2026-09-30").with(teacher()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("RECORD409_1"));
    }

    @Test
    @DisplayName("컨트롤러 경로를 과외 단위로 올린 뒤에도 기록 저장은 POST /tutorings/{id}/records로 201 · RECORD201_1이다")
    void 기록_저장_경로_유지() throws Exception {
        // given
        given(tutoringRecordService.save(eq(TEACHER_ID), eq(10L), any()))
                .willReturn(SaveRecordResponse.of(1199L, 8, 1, 8, 8, SettlementResult.notSettled()));
        String body = """
                {
                  "scheduleId": 31, "originalDate": "2026-09-30",
                  "progress": [ { "textbookId": 11, "startPosition": 7, "endPosition": 7 } ],
                  "understanding": "HIGH", "previousHomeworkResult": "DONE", "nextHomework": "7강 예습"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/tutorings/10/records")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(teacher()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("RECORD201_1"))
                .andExpect(jsonPath("$.result.sessionId").value(1199));
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
