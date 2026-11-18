package com.sudam.domain.report.controller;

import com.sudam.domain.report.dto.response.ParentReportResponse;
import com.sudam.domain.report.exception.code.ReportSuccessCode;
import com.sudam.domain.report.service.ParentReportService;
import com.sudam.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학부모 링크 화면용 API. 로그인 없이 참여자 링크 토큰으로 접근한다 (SecurityConfig.allowUris의 /api/v1/links/**).
 */
@Tag(name = "학부모 링크", description = "카카오톡 링크로 여는 학부모 화면 API (로그인 없음, 링크 토큰이 열쇠)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/links/{token}/reports")
public class ParentReportController {

    private final ParentReportService parentReportService;

    @Operation(summary = "학부모 정산 리포트 열기",
            description = "보낸(SENT) 리포트의 요약 · 진도 · 코멘트를 돌려주고, 처음 열면 열람을 기록한다. "
                    + "링크가 없거나 만료 · 폐기됐으면 PARTICIPANT404_1, 그 과외의 보낸 리포트가 아니면 REPORT404_1.")
    @GetMapping("/{reportId}")
    public ApiResponse<ParentReportResponse> get(@PathVariable String token, @PathVariable Long reportId) {
        return ApiResponse.onSuccess(ReportSuccessCode.PARENT_REPORT_FOUND, parentReportService.get(token, reportId));
    }
}
