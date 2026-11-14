package com.sudam.domain.invoice.repository;

import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.enums.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // 입금 반영 · 확인 요청 · 금액 수정이 동시에 들어와도 합계와 상태가 어긋나지 않도록 청구 행을 잠근다 (SELECT … FOR UPDATE).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

    // 수입 탭 '그 달' 청구: 선생님의 삭제되지 않은 과외(종료 포함)에서 안내일(notified_at, 없으면 created_at)이 [start, end)인 취소 안 된 청구.
    // 과외는 학생 이름 때문에 함께 가져오고, 입금 목록은 default_batch_fetch_size로 한 번에 모아 온다.
    @Query("""
            select i from Invoice i join fetch i.tutoring t
            where t.teacherId = :teacherId
              and t.deletedAt is null
              and i.status <> com.sudam.domain.invoice.enums.InvoiceStatus.CANCELED
              and coalesce(i.notifiedAt, i.createdAt) >= :start
              and coalesce(i.notifiedAt, i.createdAt) < :end
            """)
    List<Invoice> findAllOfTeacherAnnouncedBetween(@Param("teacherId") Long teacherId,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    // 선생님의 삭제되지 않은 과외에서 주어진 상태의 청구 (안내일과 관계없이). 이전 달 잔액 · 앞선 청구 잔액 계산에 쓴다.
    @Query("""
            select i from Invoice i join fetch i.tutoring t
            where t.teacherId = :teacherId
              and t.deletedAt is null
              and i.status in :statuses
            """)
    List<Invoice> findAllOfTeacherByStatusIn(@Param("teacherId") Long teacherId,
                                             @Param("statuses") Collection<InvoiceStatus> statuses);

    // 한 과외에서 주어진 상태의 청구. 입금 확인 후 그 과외의 앞선 청구 잔액을 다시 계산할 때 쓴다.
    @Query("""
            select i from Invoice i
            where i.tutoring.id = :tutoringId
              and i.status in :statuses
            """)
    List<Invoice> findAllByTutoringIdAndStatusIn(@Param("tutoringId") Long tutoringId,
                                                 @Param("statuses") Collection<InvoiceStatus> statuses);
}
