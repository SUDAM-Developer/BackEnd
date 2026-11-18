package com.sudam.domain.share.dto.request;

import com.sudam.domain.share.enums.ShareKind;
import jakarta.validation.constraints.NotNull;

/**
 * 공유 정보 생성 요청.
 *
 * @param kind          공유 종류
 * @param sourceId      원인 데이터 ID (SETTLEMENT_REPORT = report_id, PAYMENT_RESEND = invoice_id). 초대는 없음
 * @param participantId 받는 사람 (참여자 ID)
 */
public record CreateShareRequest(
        @NotNull(message = "공유 종류를 골라 주세요.")
        ShareKind kind,

        Long sourceId,

        @NotNull(message = "받는 사람을 골라 주세요.")
        Long participantId
) {
}
