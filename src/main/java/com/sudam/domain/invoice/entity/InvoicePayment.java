package com.sudam.domain.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 선생님이 확인한 입금 한 건. 일부 입금은 여러 행이 된다.
 * 청구 상태와 같은 트랜잭션에서 바뀌어야 하므로 생성 · 취소는 Invoice를 통해서만 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "invoice_payments")
public class InvoicePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    // 선생님이 확인한 금액 (원).
    @Column(nullable = false)
    private Integer amount;

    // 수입 탭 월별 합계 기준.
    @Column(nullable = false)
    private LocalDateTime confirmedAt;

    // 잘못 확인한 입금 되돌리기. 값이 있으면 합계에서 뺀다.
    private LocalDateTime canceledAt;

    InvoicePayment(Invoice invoice, int amount, LocalDateTime confirmedAt) {
        this.invoice = invoice;
        this.amount = amount;
        this.confirmedAt = confirmedAt;
    }

    public boolean isCanceled() {
        return canceledAt != null;
    }

    void cancel(LocalDateTime canceledAt) {
        this.canceledAt = canceledAt;
    }
}
