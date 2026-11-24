package com.sudam.domain.schedule.controller;

import com.sudam.domain.schedule.dto.response.DailyScheduleResponse;
import com.sudam.domain.schedule.dto.response.MonthlyScheduleResponse;
import com.sudam.domain.schedule.exception.code.ScheduleSuccessCode;
import com.sudam.domain.schedule.service.ScheduleService;
import com.sudam.global.ApiResponse;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

@Tag(name = "일정", description = "일정 탭 달력 · 날짜별 목록 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "한 달 달력 조회",
            description = "그 달 수업 점(휴강 · 보강 표시)과 학생 범례를 돌려준다. 수업이 있는 날만 날짜순. 종료한 과외는 뺀다.")
    @GetMapping("/monthly")
    public ApiResponse<MonthlyScheduleResponse> monthly(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth) {
        return ApiResponse.onSuccess(ScheduleSuccessCode.MONTHLY_FOUND,
                scheduleService.monthly(principal.getUserId(), yearMonth));
    }

    @Operation(summary = "그날 일정 · 수업 조회",
            description = "그날 수업(시각순)과, 오늘이면 지금 이후 다가오는 수업 3건을 돌려준다. date가 없으면 오늘. "
                    + "tutoringId가 내 수업 중 과외가 아니면 SCHEDULE404_1.")
    @GetMapping("/daily")
    public ApiResponse<DailyScheduleResponse> daily(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long tutoringId) {
        return ApiResponse.onSuccess(ScheduleSuccessCode.DAILY_FOUND,
                scheduleService.daily(principal.getUserId(), date, tutoringId));
    }
}
