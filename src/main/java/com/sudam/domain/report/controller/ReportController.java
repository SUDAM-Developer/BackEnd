package com.sudam.domain.report.controller;

import com.sudam.domain.report.dto.response.ReportResponse;
import com.sudam.domain.report.exception.code.ReportSuccessCode;
import com.sudam.domain.report.service.ReportService;
import com.sudam.global.ApiResponse;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정산 리포트", description = "정산 리포트 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "정산 리포트 조회",
            description = "정산 리포트의 요약(회차 · 숙제 · 이해도)과 교재별 진도를 조회한다. "
                    + "리포트가 없거나 다른 선생님 과외의 리포트면 REPORT404_1.")
    @GetMapping("/{reportId}")
    public ApiResponse<ReportResponse> get(@AuthenticationPrincipal CustomOAuth2User principal,
                                           @PathVariable Long reportId) {
        return ApiResponse.onSuccess(ReportSuccessCode.REPORT_FOUND, reportService.get(principal.getUserId(), reportId));
    }
}
