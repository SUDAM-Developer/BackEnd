package com.sudam.domain.invoice.dto.response;

import com.sudam.domain.invoice.enums.InvoiceStatus;

import java.util.List;
import java.util.Set;

/**
 * 수입 탭 화면 전체 (GET /invoices).
 * 건수와 요약은 따로 조회하지 않고 items에서 더해, 화면의 합계와 목록이 항상 맞게 한다.
 *
 * @param confirmRequestedCount 확인 대기(CONFIRM_REQUESTED) 건수
 * @param unpaidCount           PENDING · PARTIAL · CONFIRM_REQUESTED 건수
 * @param summary               items의 금액 합
 * @param items                 표시 순서로 정렬된 교육비 목록
 */
public record InvoiceListResponse(
        int confirmRequestedCount,
        int unpaidCount,
        Summary summary,
        List<InvoiceItemResponse> items
) {

    private static final Set<InvoiceStatus> UNPAID_STATUSES =
            Set.of(InvoiceStatus.PENDING, InvoiceStatus.PARTIAL, InvoiceStatus.CONFIRM_REQUESTED);

    /***
     * 함수 기능: 교육비 줄 목록을 표시 순서로 정렬하고 건수 · 요약을 그 목록에서 계산한다.
     * @param items 정렬 전 교육비 줄
     * @return InvoiceListResponse
     */
    public static InvoiceListResponse from(List<InvoiceItemResponse> items) {
        List<InvoiceItemResponse> sorted = items.stream()
                .sorted(InvoiceItemResponse.DISPLAY_ORDER)
                .toList();

        int confirmRequestedCount = (int) sorted.stream()
                .filter(item -> item.status() == InvoiceStatus.CONFIRM_REQUESTED)
                .count();
        int unpaidCount = (int) sorted.stream()
                .filter(item -> UNPAID_STATUSES.contains(item.status()))
                .count();
        Summary summary = new Summary(
                sorted.stream().mapToLong(InvoiceItemResponse::amount).sum(),
                sorted.stream().mapToLong(InvoiceItemResponse::paidAmount).sum(),
                sorted.stream().mapToLong(InvoiceItemResponse::balance).sum()
        );
        return new InvoiceListResponse(confirmRequestedCount, unpaidCount, summary, sorted);
    }

    /**
     * @param totalAmount  items의 amount 합
     * @param paidAmount   items의 paidAmount 합
     * @param unpaidAmount items의 balance 합
     */
    public record Summary(long totalAmount, long paidAmount, long unpaidAmount) {
    }
}
