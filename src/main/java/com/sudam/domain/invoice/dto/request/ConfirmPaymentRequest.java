package com.sudam.domain.invoice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

/**
 * 선생님 입금 확인 ([전액 입금 완료] · 일부 입금, 6.3.1). 두 값 모두 선택이다.
 *
 * @param amount      확인한 금액. 없으면 잔액 전부, 잔액보다 크면 잔액까지만 반영한다
 * @param confirmedAt 입금 확인 시각 (한국 시간). 없으면 요청 시각
 */
public record ConfirmPaymentRequest(
        @Schema(description = "확인한 금액(원). 없으면 잔액 전부", example = "120000")
        @Positive(message = "입금 금액은 1원 이상이어야 합니다.")
        Integer amount,

        @Schema(description = "입금 확인 시각(한국 시간). 없으면 요청 시각", example = "2026-11-15T20:00:00")
        LocalDateTime confirmedAt
) {

    // 본문 없이 호출하면 잔액 전부 · 지금 시각으로 처리한다.
    public static ConfirmPaymentRequest empty() {
        return new ConfirmPaymentRequest(null, null);
    }
}
