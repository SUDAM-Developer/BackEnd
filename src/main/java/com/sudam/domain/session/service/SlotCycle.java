package com.sudam.domain.session.service;

/**
 * 회차 1개의 사이클 정보. 화면의 'n/N회차' · '새 사이클 n회차'를 그린다.
 *
 * @param cycleNo                소속(예측이면 예상) 사이클 번호
 * @param noInCycle              사이클 안 번호. 미인정 휴강이면 null
 * @param recognizedCount        그 사이클의 인정 회차 수. 아직 열리지 않은 예측 사이클이면 0
 * @param settlementSessionCount 그 사이클의 정산 회차 (정책 이력)
 * @param startsNewCycle         예측상 진행 중 사이클 다음 사이클의 첫 회차
 * @param isPredicted            아직 기록하지 않은 회차라 번호가 예측값
 */
public record SlotCycle(
        int cycleNo,
        Integer noInCycle,
        int recognizedCount,
        int settlementSessionCount,
        boolean startsNewCycle,
        boolean isPredicted
) {
}
