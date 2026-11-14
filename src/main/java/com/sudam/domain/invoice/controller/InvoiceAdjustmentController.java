package com.sudam.domain.invoice.controller;

import com.sudam.domain.invoice.dto.request.AdjustInvoiceAmountRequest;
import com.sudam.domain.invoice.dto.response.ReportInvoiceResponse;
import com.sudam.domain.invoice.exception.code.InvoiceSuccessCode;
import com.sudam.domain.invoice.service.ReportInvoiceService;
import com.sudam.global.ApiResponse;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 수정 이력 행을 하나 만드는 요청이지만 화면은 바뀐 교육비 안내만 다시 그리므로 201이 아니라 200으로 응답한다.
@Tag(name = "교육비")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/invoices/{invoiceId}/adjustments")
public class InvoiceAdjustmentController {

    private final ReportInvoiceService reportInvoiceService;

    @Operation(summary = "교육비 수정",
            description = "T-14 교육비 수정 시트 [저장]. 금액을 바꾸고 수정 이력을 남긴 뒤 교육비 안내와 같은 구조로 돌려준다. "
                    + "같은 금액이면 아무것도 바꾸지 않는다. 내 과외의 청구가 아니면 INVOICE404_1, 취소된 청구 INVOICE409_1, "
                    + "이미 받은 금액보다 적으면 INVOICE409_2, 보낸 리포트의 교육비면 INVOICE409_4.")
    @PostMapping
    public ApiResponse<ReportInvoiceResponse> adjustAmount(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @PathVariable Long invoiceId,
            @Valid @RequestBody AdjustInvoiceAmountRequest request) {

        return ApiResponse.onSuccess(InvoiceSuccessCode.AMOUNT_ADJUSTED,
                reportInvoiceService.adjustAmount(principal.getUserId(), invoiceId, request));
    }
}
