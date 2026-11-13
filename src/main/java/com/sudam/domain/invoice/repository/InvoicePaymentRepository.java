package com.sudam.domain.invoice.repository;

import com.sudam.domain.invoice.entity.InvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, Long> {
}
