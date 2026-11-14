package com.sudam.domain.invoice.controller;

import com.sudam.domain.invoice.dto.response.ReportInvoiceResponse;
import com.sudam.domain.invoice.exception.code.InvoiceSuccessCode;
import com.sudam.domain.invoice.service.ReportInvoiceService;
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

// 리포트 하위 경로지만 청구가 report_id를 가지므로 교육비 도메인에 둔다. 리포트 조회(ReportController)와는 따로다.
@Tag(name = "교육비")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reports/{reportId}/invoice")
public class ReportInvoiceController {

    private final ReportInvoiceService reportInvoiceService;

    @Operation(summary = "정산 리포트 교육비 안내 조회",
            description = "T-14 정산 리포트의 교육비 안내 블록. 리포트에 붙은 취소 안 된 청구 1건을 돌려준다. "
                    + "청구가 없거나 내 과외의 리포트가 아니면 INVOICE404_1.")
    @GetMapping
    public ApiResponse<ReportInvoiceResponse> getReportInvoice(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @PathVariable Long reportId) {

        return ApiResponse.onSuccess(InvoiceSuccessCode.REPORT_INVOICE_FOUND,
                reportInvoiceService.getReportInvoice(principal.getUserId(), reportId));
    }
}
