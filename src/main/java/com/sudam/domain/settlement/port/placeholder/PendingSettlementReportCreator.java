package com.sudam.domain.settlement.port.placeholder;

import com.sudam.domain.settlement.port.SettlementReportCreator;
import org.springframework.stereotype.Component;

/**
 * 정산 리포트 구현(김강민)이 병합되기 전까지 앱이 부팅되도록 두는 임시 구현. 호출되면 예외를 던진다.
 * 회차 · 리포트 구현이 병합되는 PR에서 이 패키지를 지운다 (남겨 두면 같은 타입 빈이 두 개가 되어 부팅이 실패한다).
 */
@Component
public class PendingSettlementReportCreator implements SettlementReportCreator {

    @Override
    public Long create(Long tutoringId, Long cycleId) {
        throw new UnsupportedOperationException("김강민 구현 병합 전");
    }
}
