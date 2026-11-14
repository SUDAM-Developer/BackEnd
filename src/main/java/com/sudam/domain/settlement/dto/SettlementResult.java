package com.sudam.domain.settlement.dto;

/**
 * 정산 실행 결과. 기록 저장 API가 응답의 settlement { settled, cycleId, reportId, invoiceId }로 그대로 싣는다.
 *
 * @param settled   이번 기록 저장으로 정산했는지
 * @param cycleId   정산한(방금 닫은) 사이클 ID. 정산하지 않았으면 NULL
 * @param reportId  만든 정산 리포트 ID. 정산하지 않았으면 NULL
 * @param invoiceId 만든 교육비 청구 ID. 정산하지 않았으면 NULL
 */
public record SettlementResult(boolean settled, Long cycleId, Long reportId, Long invoiceId) {

    private static final SettlementResult NOT_SETTLED = new SettlementResult(false, null, null, null);

    public static SettlementResult notSettled() {
        return NOT_SETTLED;
    }

    public static SettlementResult settled(Long cycleId, Long reportId, Long invoiceId) {
        return new SettlementResult(true, cycleId, reportId, invoiceId);
    }
}
