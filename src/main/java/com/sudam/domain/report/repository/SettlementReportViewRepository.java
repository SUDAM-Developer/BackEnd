package com.sudam.domain.report.repository;

import com.sudam.domain.report.entity.SettlementReportView;
import com.sudam.domain.report.entity.SettlementReportViewId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    // 첫 열람만 남긴다. 이미 행이 있으면 첫 열람 시각을 그대로 둔다.
    // 네이티브 쿼리인 이유: JPQL에는 값을 넣는 INSERT가 없고, '있는지 보고 저장'은 같은 학부모가 동시에 열면
    // 둘 다 없다고 보고 PK 충돌이 난다. 중복 키만 무시하도록 IGNORE 대신 ON DUPLICATE KEY UPDATE를 쓴다
    // (IGNORE는 FK 위반까지 경고로 삼킨다).
    @Modifying
    @Query(value = """
            insert into settlement_report_views (report_id, participant_id, first_viewed_at)
            values (:reportId, :participantId, :viewedAt)
            on duplicate key update first_viewed_at = first_viewed_at
            """, nativeQuery = true)
    int insertFirstView(@Param("reportId") Long reportId,
                        @Param("participantId") Long participantId,
                        @Param("viewedAt") LocalDateTime viewedAt);
}
