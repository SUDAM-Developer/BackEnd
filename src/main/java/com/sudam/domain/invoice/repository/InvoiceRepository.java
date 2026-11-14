package com.sudam.domain.invoice.repository;

import com.sudam.domain.invoice.entity.Invoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // 입금 반영 · 확인 요청 · 금액 수정이 동시에 들어와도 합계와 상태가 어긋나지 않도록 청구 행을 잠근다 (SELECT … FOR UPDATE).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);
}
