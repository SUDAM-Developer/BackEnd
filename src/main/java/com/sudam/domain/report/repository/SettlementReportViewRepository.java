package com.sudam.domain.report.repository;

import com.sudam.domain.report.entity.SettlementReportView;
import com.sudam.domain.report.entity.SettlementReportViewId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SettlementReportViewRepository extends JpaRepository<SettlementReportView, SettlementReportViewId> {

    // 여러 리포트의 첫 열람 시각을 한 쿼리로 모은다 (리포트 목록의 읽음 표시). 학부모가 둘이면 먼저 연 시각.
    // 열람 기록이 없는 리포트는 결과에 없다.
    @Query("""
            select new com.sudam.domain.report.repository.ReportFirstViewedAt(v.id.reportId, min(v.firstViewedAt))
            from SettlementReportView v
            where v.id.reportId in :reportIds
            group by v.id.reportId
            """)
    List<ReportFirstViewedAt> findFirstViewedAts(@Param("reportIds") Collection<Long> reportIds);
}
