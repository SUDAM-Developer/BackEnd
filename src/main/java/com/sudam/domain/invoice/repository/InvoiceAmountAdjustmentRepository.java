package com.sudam.domain.invoice.repository;

import com.sudam.domain.invoice.entity.InvoiceAmountAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceAmountAdjustmentRepository extends JpaRepository<InvoiceAmountAdjustment, Long> {
}
