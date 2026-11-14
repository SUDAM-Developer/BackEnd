package com.sudam.domain.invoice.controller;

import com.sudam.domain.invoice.dto.request.ConfirmPaymentRequest;
import com.sudam.domain.invoice.dto.response.InvoiceListResponse;
import com.sudam.domain.invoice.dto.response.PaymentConfirmResponse;
import com.sudam.domain.invoice.exception.code.InvoiceSuccessCode;
import com.sudam.domain.invoice.service.InvoiceService;
import com.sudam.global.ApiResponse;
import com.sudam.global.idempotency.IdempotencyInterceptor;
import com.sudam.global.idempotency.Idempotent;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

// 단일 파라미터(month) 제약은 스프링 내장 메서드 검증으로 처리한다.
// 클래스에 @Validated를 붙이면 AOP 검증으로 바뀌어 ConstraintViolationException(→ COMMON500)이 나므로 붙이지 않는다.
@Tag(name = "교육비", description = "수입 탭 입금 현황 · 입금 확인 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private static final String MONTH_PATTERN = "^\\d{4}-(0[1-9]|1[0-2])$";

    private final InvoiceService invoiceService;

    @Operation(summary = "입금 현황 조회",
            description = "수입 탭 화면 전체. 안내일이 그 달인 청구와 그 달 이전의 잔액 남은 청구를 "
                    + "확인 대기 → 입금 대기 → 일부 입금 → 입금 완료 순으로 돌려준다. month가 없으면 한국 시간 이번 달.")
    @GetMapping
    public ApiResponse<InvoiceListResponse> getInvoices(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @Parameter(description = "조회할 달 (YYYY-MM). 없으면 이번 달", example = "2026-11")
            @RequestParam(required = false)
            @Pattern(regexp = MONTH_PATTERN, message = "월은 YYYY-MM 형식이어야 합니다.") String month) {

        YearMonth target = month == null ? null : YearMonth.parse(month);
        return ApiResponse.onSuccess(InvoiceSuccessCode.INVOICES_FOUND,
                invoiceService.getInvoices(principal.getUserId(), target));
    }

    @Operation(summary = "입금 확인",
            description = "선생님이 확인한 입금을 기록한다. amount가 없으면 잔액 전부, 잔액보다 크면 잔액까지만 반영한다. "
                    + "내 과외의 청구가 아니면 INVOICE404_1, 취소된 청구는 INVOICE409_1, 이미 완납이면 INVOICE409_3. "
                    + "Idempotency-Key(UUID) 헤더 필수: 24시간 동안 같은 키로 다시 보내면 처리 없이 첫 성공 응답을 그대로 돌려준다 "
                    + "(응답 헤더 Idempotent-Replayed: true). 헤더 없음 · 형식 오류 IDEMPOTENCY400_1, "
                    + "같은 키 처리 중 IDEMPOTENCY409_1, 같은 키 · 다른 요청 IDEMPOTENCY422_1.")
    @Parameter(name = IdempotencyInterceptor.HEADER, in = ParameterIn.HEADER, required = true,
            description = "요청마다 새로 만든 UUID. 재시도할 때는 같은 값을 보낸다",
            schema = @Schema(type = "string", format = "uuid"))
    @Idempotent
    @PostMapping("/{invoiceId}/payments")
    public ResponseEntity<ApiResponse<PaymentConfirmResponse>> confirmPayment(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @PathVariable Long invoiceId,
            @Valid @RequestBody(required = false) ConfirmPaymentRequest request) {

        PaymentConfirmResponse response = invoiceService.confirmPayment(principal.getUserId(), invoiceId,
                request != null ? request : ConfirmPaymentRequest.empty());
        return ResponseEntity
                .status(InvoiceSuccessCode.PAYMENT_CONFIRMED.getStatus())
                .body(ApiResponse.onSuccess(InvoiceSuccessCode.PAYMENT_CONFIRMED, response));
    }
}
