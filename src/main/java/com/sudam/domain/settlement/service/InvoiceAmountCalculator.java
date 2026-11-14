package com.sudam.domain.settlement.service;

import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.tutoring.entity.TutoringPolicy;

/**
 * 교육비 금액 계산 순수 함수 모음. 정산 회차를 다 채운 정산은 수업료 그대로라 계산이 필요 없고,
 * 회차가 모자랄 때(수업 종료 7.1.2, UT 이후)만 이 계산을 쓴다.
 */
public final class InvoiceAmountCalculator {

    private InvoiceAmountCalculator() {
    }

    /***
     * 함수 기능: 회차가 모자랄 때 청구 금액 = 인정 회차 × 회당 단가.
     *          회당 단가는 청구 컬럼 · 스냅샷과 같은 계산(Invoice.unitPriceOf, 원 미만 버림)을 쓴다.
     * @param recognized 인정 회차 수 (0 이상)
     * @param policy     적용 정책
     * @return 금액 (원)
     */
    public static int shortfall(int recognized, TutoringPolicy policy) {
        if (recognized < 0) {
            throw new IllegalArgumentException("인정 회차는 0 이상이어야 합니다.");
        }
        return recognized * Invoice.unitPriceOf(policy);
    }
}
