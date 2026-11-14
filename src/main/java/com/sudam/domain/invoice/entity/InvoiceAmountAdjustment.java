package com.sudam.domain.invoice.entity;

import com.sudam.domain.invoice.enums.AdjustedBy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 교육비 금액 수정 이력 (6.1.3). 수정 사유는 교육비 카드에 '금액 수정 사유'로 보여준다.
 * 금액과 이력이 어긋나지 않도록 Invoice.adjustAmount에서만 만든다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "invoice_amount_adjustments")
public class InvoiceAmountAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false)
    private Integer beforeAmount;

    @Column(nullable = false)
    private Integer afterAmount;

    @Column(nullable = false, length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'TEACHER'")
    @Column(nullable = false, length = 20)
    private AdjustedBy adjustedBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    InvoiceAmountAdjustment(Invoice invoice, int beforeAmount, int afterAmount, String reason,
                            AdjustedBy adjustedBy) {
        this.invoice = invoice;
        this.beforeAmount = beforeAmount;
        this.afterAmount = afterAmount;
        this.reason = reason;
        this.adjustedBy = adjustedBy;
    }
}
