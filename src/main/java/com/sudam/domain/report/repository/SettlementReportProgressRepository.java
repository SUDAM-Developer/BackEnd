package com.sudam.domain.report.repository;

import com.sudam.domain.report.entity.SettlementReportProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementReportProgressRepository extends JpaRepository<SettlementReportProgress, Long> {

    // 리포트의 진도 표를 표시 순서대로 가져온다.
    List<SettlementReportProgress> findAllByReportIdOrderBySortOrder(Long reportId);
}
