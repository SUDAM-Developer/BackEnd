package com.sudam.domain.invoice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 교육비 수정 시트 [저장] (T-14, 6.1.3).
 *
 * @param amount 바꿀 최종 금액(원). 0 이상 10,000,000 이하
 * @param reason 수정 사유. 교육비 카드에 '금액 수정 사유'로 보인다
 */
public record AdjustInvoiceAmountRequest(
        @Schema(description = "바꿀 최종 금액(원)", example = "375000")
        @NotNull(message = "교육비를 입력해 주세요.")
        @Min(value = 0, message = "교육비는 0원 이상이어야 합니다.")
        @Max(value = 10_000_000, message = "교육비는 10,000,000원 이하여야 합니다.")
        Integer amount,

        @Schema(description = "수정 사유 (200자 이내)", example = "교재비 15,000원 추가")
        @NotBlank(message = "수정 사유를 입력해 주세요.")
        @Size(max = 200, message = "수정 사유는 200자 이내여야 합니다.")
        String reason
) {
}
