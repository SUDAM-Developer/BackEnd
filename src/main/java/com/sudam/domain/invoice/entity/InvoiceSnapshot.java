package com.sudam.domain.invoice.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.PaymentTiming;

import java.time.LocalDate;
import java.util.List;

/**
 * 교육비 계산 근거 (invoices.snapshot, JSON). 교육비 카드의 '계산 근거'(6.1.2)를 이 값으로 보여준다.
 * 레코드 컴포넌트 이름이 곧 JSON 키다. 이미 저장된 행을 읽을 수 있어야 하므로 이름을 바꾸지 않는다.
 *
 * @param recognizedCount        인정 회차. 정산한 사이클 기준. 정산 없이 만든 청구(선불 첫 청구 FIRST)는 NULL
 * @param unitPrice              회당 단가 = 수업료 ÷ 정산 회차, 원 미만 버림
 * @param settlementSessionCount 정산 회차
 * @param paymentTiming          선불 / 후불
 * @param cycleStartDate         사이클 시작일. 정산한 사이클 기준. 정산 없이 만든 청구(선불 첫 청구 FIRST)는 NULL
 * @param canceledLessons        사이클 기간 안의 휴강 내역. 없으면 빈 목록
 */
public record InvoiceSnapshot(
        Integer recognizedCount,
        Integer unitPrice,
        Integer settlementSessionCount,
        PaymentTiming paymentTiming,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate cycleStartDate,
        List<CanceledLesson> canceledLessons
) {

    public InvoiceSnapshot {
        canceledLessons = canceledLessons == null ? List.of() : List.copyOf(canceledLessons);
    }

    /***
     * 함수 기능: 적용 정책에서 단가 · 정산 회차 · 선불/후불을 꺼내 스냅샷을 만든다.
     *          청구 컬럼(unit_price 등)과 같은 계산을 쓰도록 단가는 Invoice.unitPriceOf로 구한다.
     * @param policy          이 청구에 적용한 정책
     * @param recognizedCount 인정 회차. 정산한 사이클 기준 (정산 없이 만든 청구 = 선불 첫 청구 FIRST는 NULL)
     * @param cycleStartDate  사이클 시작일. 정산한 사이클 기준 (정산 없이 만든 청구 = 선불 첫 청구 FIRST는 NULL)
     * @param canceledLessons 휴강 내역 (NULL이면 빈 목록)
     * @return InvoiceSnapshot
     */
    public static InvoiceSnapshot of(TutoringPolicy policy, Integer recognizedCount, LocalDate cycleStartDate,
                                     List<CanceledLesson> canceledLessons) {
        return new InvoiceSnapshot(
                recognizedCount,
                Invoice.unitPriceOf(policy),
                policy.getSettlementSessionCnt(),
                policy.getPaymentTiming(),
                cycleStartDate,
                canceledLessons
        );
    }
}
