package com.sudam.domain.session.controller;

import com.sudam.domain.session.dto.response.TodaySessionsResponse;
import com.sudam.domain.session.exception.code.SessionSuccessCode;
import com.sudam.domain.session.service.TodaySessionService;
import com.sudam.global.ApiResponse;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "수업", description = "홈 화면 오늘 수업 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final TodaySessionService todaySessionService;

    @Operation(summary = "오늘 수업 목록 조회",
            description = "선생님의 오늘 수업(시작 시각순)을 돌려준다. 휴강 · 기록 완료 회차도 포함하고 종료 · 삭제한 과외는 뺀다.")
    @GetMapping("/today")
    public ApiResponse<TodaySessionsResponse> today(@AuthenticationPrincipal CustomOAuth2User principal) {
        return ApiResponse.onSuccess(SessionSuccessCode.TODAY_FOUND,
                todaySessionService.today(principal.getUserId()));
    }
}
