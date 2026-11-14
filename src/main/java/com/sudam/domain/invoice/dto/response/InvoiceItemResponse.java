package com.sudam.domain.invoice.dto.response;

import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.entity.InvoicePayment;
import com.sudam.domain.invoice.enums.InvoiceStatus;
import com.sudam.domain.invoice.enums.InvoiceStatusLineType;
import com.sudam.domain.tutoring.entity.Tutoring;

import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * 수입 탭 교육비 한 줄. 입금 현황 목록(GET /invoices)과 입금 확인 응답(POST /invoices/{id}/payments)이
 * 이 변환 하나를 같이 쓴다. 금액 이름의 뜻은 docs/api-spec.md '계산 값 이름'을 따른다.
 *
 * @param invoiceId            청구 ID
 * @param tutoringId           과외 ID
 * @param studentName          학생 이름 (과외 삭제 시 NULL)
 * @param invoiceName          첫 N회차 / 다음 N회차 / 이번 N회차 / 마지막 정산
 * @param amount               최종 청구 금액
 * @param paidAmount           취소되지 않은 입금 합
 * @param balance              남은 금액 (amount − paidAmount, 0 이상)
 * @param previousUnpaidAmount 같은 과외의 앞선 청구(취소 제외) 잔액 합
 * @param status               청구 상태
 * @param statusLine           카드 아래 한 줄 안내
 * @param reportId             함께 보낸 정산 리포트 ID
 * @param notifiedAt           교육비 안내 첫 발송 시각
 * @param createdAt            청구 생성 시각
 */
public record InvoiceItemResponse(
        Long invoiceId,
        Long tutoringId,
        String studentName,
        String invoiceName,
        int amount,
        int paidAmount,
        int balance,
        int previousUnpaidAmount,
        InvoiceStatus status,
        StatusLine statusLine,
        Long reportId,
        LocalDateTime notifiedAt,
        LocalDateTime createdAt
) {

    // 확인 대기 → 입금 대기 → 일부 입금 → 입금 완료, 같은 상태는 안내일(없으면 생성일) 최신순 · ID 큰 순.
    static final Comparator<InvoiceItemResponse> DISPLAY_ORDER =
            Comparator.comparingInt((InvoiceItemResponse item) -> statusRank(item.status()))
                    .thenComparing(InvoiceItemResponse::announcedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(InvoiceItemResponse::invoiceId, Comparator.nullsLast(Comparator.reverseOrder()));

    /***
     * 함수 기능: 청구 하나를 수입 탭 한 줄로 바꾼다. 과외 · 입금 목록이 이미 로딩된 트랜잭션 안에서 호출한다.
     * @param invoice               변환할 청구
     * @param previousUnpaidAmount  같은 과외의 앞선 청구 잔액 합 (서비스가 계산)
     * @param unpaidReminderEnabled 과외 공유 설정의 미입금 알림 켜짐 여부
     * @return InvoiceItemResponse
     */
    public static InvoiceItemResponse of(Invoice invoice, int previousUnpaidAmount, boolean unpaidReminderEnabled) {
        Tutoring tutoring = invoice.getTutoring();
        int paidAmount = invoice.paidAmount();
        return new InvoiceItemResponse(
                invoice.getId(),
                tutoring.getId(),
                tutoring.getStudentName(),
                nameOf(invoice),
                invoice.getAmount(),
                paidAmount,
                Math.max(0, invoice.getAmount() - paidAmount),
                previousUnpaidAmount,
                invoice.getStatus(),
                StatusLine.of(invoice, unpaidReminderEnabled),
                invoice.getReportId(),
                invoice.getNotifiedAt(),
                invoice.getCreatedAt()
        );
    }

    // 수입 탭 이름 (6.1.2). N은 청구 스냅샷의 정산 회차다.
    private static String nameOf(Invoice invoice) {
        int count = invoice.getSnapshot().settlementSessionCount();
        return switch (invoice.getKind()) {
            case FIRST -> "첫 " + count + "회차";
            case NEXT -> "다음 " + count + "회차";
            case CURRENT -> "이번 " + count + "회차";
            case FINAL -> "마지막 정산";
        };
    }

    private static int statusRank(InvoiceStatus status) {
        return switch (status) {
            case CONFIRM_REQUESTED -> 0;
            case PENDING -> 1;
            case PARTIAL -> 2;
            case PAID -> 3;
            case CANCELED -> 4;
        };
    }

    // '이번 달' 판단과 같은 기준: 안내를 보냈으면 안내 시각, 아니면 생성 시각.
    private LocalDateTime announcedAt() {
        return notifiedAt != null ? notifiedAt : createdAt;
    }

    /**
     * 카드 아래 한 줄 안내.
     *
     * @param type 안내 종류
     * @param at   안내에 함께 보여줄 시각. 시각이 없는 종류는 NULL
     */
    public record StatusLine(InvoiceStatusLineType type, LocalDateTime at) {

        /***
         * 함수 기능: 위에서부터 먼저 맞는 하나를 고른다.
         *          확인 대기 → 일부 입금 → 다시 보냄 → 미입금 알림 예정 → 안내 전 → 없음.
         *          입금 완료는 안내를 다시 보냈거나 안내 전이어도 더 알릴 것이 없으므로 NONE이다.
         */
        static StatusLine of(Invoice invoice, boolean unpaidReminderEnabled) {
            InvoiceStatus status = invoice.getStatus();
            if (status == InvoiceStatus.CONFIRM_REQUESTED) {
                return new StatusLine(InvoiceStatusLineType.PARENT_CONFIRMED, invoice.getConfirmRequestedAt());
            }
            if (status == InvoiceStatus.PARTIAL) {
                return new StatusLine(InvoiceStatusLineType.PARTIAL_PAID, lastConfirmedAt(invoice));
            }
            if (status == InvoiceStatus.PAID) {
                return new StatusLine(InvoiceStatusLineType.NONE, null);
            }
            if (invoice.getRenotifiedAt() != null) {
                return new StatusLine(InvoiceStatusLineType.RESENT, invoice.getRenotifiedAt());
            }
            if (unpaidReminderEnabled && invoice.getReminderSentAt() == null && status == InvoiceStatus.PENDING) {
                return new StatusLine(InvoiceStatusLineType.REMINDER_SCHEDULED, null);
            }
            if (invoice.getNotifiedAt() == null) {
                return new StatusLine(InvoiceStatusLineType.NOT_SENT, null);
            }
            return new StatusLine(InvoiceStatusLineType.NONE, null);
        }

        private static LocalDateTime lastConfirmedAt(Invoice invoice) {
            return invoice.getPayments().stream()
                    .filter(payment -> !payment.isCanceled())
                    .map(InvoicePayment::getConfirmedAt)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
        }
    }
}
