package com.sudam.domain.report.repository;

import com.sudam.domain.report.entity.SettlementReport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SettlementReportRepository extends JpaRepository<SettlementReport, Long> {

    // 사이클의 리포트. 리포트를 만들 때 직전 사이클의 수행률을 가져오는 데 쓴다.
    Optional<SettlementReport> findByCycleId(Long cycleId);

    // 리포트와 과외를 함께 조회한다. 조회 API가 선생님 확인 · 학생 이름에 과외를 쓰므로 한 쿼리로 가져온다.
    @Query("select r from SettlementReport r join fetch r.tutoring where r.id = :reportId")
    Optional<SettlementReport> findWithTutoringById(@Param("reportId") Long reportId);

    // 코멘트 저장과 발송 확인이 동시에 들어와도 서로 덮어쓰지 않도록 리포트 행을 잠근다 (SELECT … FOR UPDATE).
    // 과외는 join fetch하지 않는다. 함께 잠그면 과외 행을 잠그는 정산 · 사이클 처리와 엉킬 수 있다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SettlementReport r where r.id = :reportId")
    Optional<SettlementReport> findByIdForUpdate(@Param("reportId") Long reportId);
}
