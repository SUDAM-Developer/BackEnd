package com.sudam.domain.report.repository;

import com.sudam.domain.report.entity.SettlementReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SettlementReportRepository extends JpaRepository<SettlementReport, Long> {

    // 사이클의 리포트. 리포트를 만들 때 직전 사이클의 수행률을 가져오는 데 쓴다.
    Optional<SettlementReport> findByCycleId(Long cycleId);

    // 리포트와 과외를 함께 조회한다. 조회 API가 선생님 확인 · 학생 이름에 과외를 쓰므로 한 쿼리로 가져온다.
    @Query("select r from SettlementReport r join fetch r.tutoring where r.id = :reportId")
    Optional<SettlementReport> findWithTutoringById(@Param("reportId") Long reportId);
}
