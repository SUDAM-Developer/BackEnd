package com.sudam.domain.report.entity;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.report.enums.ReportStatus;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 정산 리포트 (사이클당 1개). 정산 회차를 채운 기록 저장 순간 그 사이클의 기록으로 통계를 계산해 저장한다.
 * 기록을 나중에 고쳐도 다시 계산하지 않는 스냅샷이다. 코멘트 · AI 초안 · 발송 컬럼은 해당 기능과 함께 추가한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "settlement_reports",
        indexes = @Index(name = "idx_settlement_reports_tutoring_status", columnList = "tutoring_id, status")
)
public class SettlementReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutoring_id", nullable = false)
    private Tutoring tutoring;

    // 사이클당 리포트 1개. UNIQUE라 같은 사이클을 두 번 정산하면 저장 단계에서 막힌다.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false, unique = true)
    private Cycle cycle;

    // 진행한 인정 회차 수
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer completedCount;

    // 정산 회차 (정산 시점 정책 값)
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer targetCount;

    // 사이클 기간 안의 휴강(CANCELED) 회차 수
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer canceledCount;

    // 숙제 수행률 %. 지난 숙제 결과가 있는 기록이 하나도 없으면 null
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer homeworkRate;

    // 직전 사이클 리포트의 숙제 수행률. 비교 표시용 스냅샷이며 직전 리포트가 없으면 null
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer prevHomeworkRate;

    // 이해도 상 · 중 · 하 횟수
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer understandingHigh;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer understandingMedium;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer understandingLow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    /***
     * 함수 기능: 정산 시점에 계산한 통계로 리포트를 만든다. 상태는 GENERATED로 시작한다.
     */
    @Builder
    private SettlementReport(Tutoring tutoring, Cycle cycle, int completedCount, int targetCount, int canceledCount,
                             Integer homeworkRate, Integer prevHomeworkRate,
                             int understandingHigh, int understandingMedium, int understandingLow) {
        this.tutoring = tutoring;
        this.cycle = cycle;
        this.completedCount = completedCount;
        this.targetCount = targetCount;
        this.canceledCount = canceledCount;
        this.homeworkRate = homeworkRate;
        this.prevHomeworkRate = prevHomeworkRate;
        this.understandingHigh = understandingHigh;
        this.understandingMedium = understandingMedium;
        this.understandingLow = understandingLow;
        this.status = ReportStatus.GENERATED;
    }
}
