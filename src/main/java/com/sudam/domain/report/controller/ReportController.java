package com.sudam.domain.report.controller;

import com.sudam.domain.report.dto.request.SaveReportCommentRequest;
import com.sudam.domain.report.dto.response.ReportCommentResponse;
import com.sudam.domain.report.dto.response.ReportDeliveryListResponse;
import com.sudam.domain.report.dto.response.ReportRecipientListResponse;
import com.sudam.domain.report.dto.response.ReportResponse;
import com.sudam.domain.report.dto.response.ReportSendResponse;
import com.sudam.domain.report.exception.code.ReportSuccessCode;
import com.sudam.domain.report.service.ReportCommentService;
import com.sudam.domain.report.service.ReportSendService;
import com.sudam.domain.report.service.ReportService;
import com.sudam.global.ApiResponse;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정산 리포트", description = "정산 리포트 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportCommentService reportCommentService;
    private final ReportSendService reportSendService;

    @Operation(summary = "정산 리포트 목록 조회",
            description = "로그인한 선생님의 정산 리포트를 최근에 만든 순으로 조회한다. 리포트마다 발송 상태(GENERATED / SENT)와 "
                    + "발송 시각을 담는다. 삭제한 과외의 리포트는 빠진다. 리포트가 없으면 items가 빈 배열.")
    @GetMapping("/deliveries")
    public ApiResponse<ReportDeliveryListResponse> listDeliveries(@AuthenticationPrincipal CustomOAuth2User principal) {
        return ApiResponse.onSuccess(ReportSuccessCode.DELIVERIES_FOUND,
                reportService.listDeliveries(principal.getUserId()));
    }

    @Operation(summary = "정산 리포트 조회",
            description = "정산 리포트의 요약(회차 · 숙제 · 이해도)과 교재별 진도를 조회한다. "
                    + "리포트가 없거나 다른 선생님 과외의 리포트면 REPORT404_1.")
    @GetMapping("/{reportId}")
    public ApiResponse<ReportResponse> get(@AuthenticationPrincipal CustomOAuth2User principal,
                                           @PathVariable Long reportId) {
        return ApiResponse.onSuccess(ReportSuccessCode.REPORT_FOUND, reportService.get(principal.getUserId(), reportId));
    }

    @Operation(summary = "정산 리포트 받는 사람 조회",
            description = "리포트를 받을 학부모(삭제되지 않았고 링크가 있는 사람)와 각자의 학부모 리포트 화면 URL을 돌려준다. "
                    + "카카오톡 공유 메시지에 넣는다. 없거나 남의 리포트면 REPORT404_1.")
    @GetMapping("/{reportId}/recipients")
    public ApiResponse<ReportRecipientListResponse> listRecipients(
            @AuthenticationPrincipal CustomOAuth2User principal, @PathVariable Long reportId) {
        return ApiResponse.onSuccess(ReportSuccessCode.RECIPIENTS_FOUND,
                reportService.listRecipients(principal.getUserId(), reportId));
    }

    @Operation(summary = "정산 리포트 코멘트 조회",
            description = "코멘트 · 출처('AI 초안' 배지) · 선생님 확인 여부를 조회한다. 코멘트가 없으면 comment · commentSource가 null. "
                    + "aiStatus · softenedSentences는 AI 기능 전이라 비어 있다. 없거나 남의 리포트면 REPORT404_1.")
    @GetMapping("/{reportId}/comment")
    public ApiResponse<ReportCommentResponse> getComment(@AuthenticationPrincipal CustomOAuth2User principal,
                                                         @PathVariable Long reportId) {
        return ApiResponse.onSuccess(ReportSuccessCode.COMMENT_FOUND,
                reportCommentService.get(principal.getUserId(), reportId));
    }

    @Operation(summary = "정산 리포트 코멘트 저장 · 확인",
            description = "코멘트를 저장하고 확인 처리한다. 출처는 TEACHER가 된다. "
                    + "이미 보낸 리포트면 REPORT409_1, 없거나 남의 리포트면 REPORT404_1.")
    @PutMapping("/{reportId}/comment")
    public ApiResponse<ReportCommentResponse> saveComment(@AuthenticationPrincipal CustomOAuth2User principal,
                                                          @PathVariable Long reportId,
                                                          @Valid @RequestBody SaveReportCommentRequest request) {
        return ApiResponse.onSuccess(ReportSuccessCode.COMMENT_SAVED,
                reportCommentService.save(principal.getUserId(), reportId, request.text()));
    }

    @Operation(summary = "정산 리포트 발송 확인",
            description = "카카오톡 공유 창을 띄운 뒤 호출한다. 리포트를 SENT로 바꾸고, 이미 SENT면 첫 발송 시각을 그대로 돌려준다. "
                    + "코멘트 확인 전이면 REPORT409_2, 없거나 남의 리포트면 REPORT404_1.")
    @PostMapping("/{reportId}/send")
    public ApiResponse<ReportSendResponse> send(@AuthenticationPrincipal CustomOAuth2User principal,
                                                @PathVariable Long reportId) {
        return ApiResponse.onSuccess(ReportSuccessCode.REPORT_SENT,
                reportSendService.send(principal.getUserId(), reportId));
    }
}
