package com.sudam.domain.report.entity;

import com.sudam.domain.report.enums.ReportTextbookStatus;
import com.sudam.domain.tutoring.entity.Textbook;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 리포트 진도 표의 한 줄 (교재 1개). 교재 이름과 진도를 문자열로 굳혀 두어,
 * 나중에 교재 이름이나 기록을 고쳐도 이미 만든 진도 표가 바뀌지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "settlement_report_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_settlement_report_progress_report_textbook",
                columnNames = {"report_id", "textbook_id"}
        )
)
public class SettlementReportProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private SettlementReport report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "textbook_id", nullable = false)
    private Textbook textbook;

    // 생성 시점의 교재 이름 스냅샷
    @Column(nullable = false, length = 20)
    private String textbookName;

    // 이 사이클의 첫 진도 (예: "Unit 12")
    @Column(nullable = false, length = 30)
    private String startLabel;

    // 이 사이클의 마지막 진도
    @Column(nullable = false, length = 30)
    private String endLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportTextbookStatus textbookStatus;

    // 진도 표 안의 순서 (0부터)
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer sortOrder;

    private SettlementReportProgress(SettlementReport report, Textbook textbook, String startLabel, String endLabel,
                                     int sortOrder) {
        this.report = report;
        this.textbook = textbook;
        this.textbookName = textbook.getName();
        this.startLabel = startLabel;
        this.endLabel = endLabel;
        // 교재 추가 · 사용 종료 기능 전에는 사이클 중간에 교재가 바뀌지 않으므로 항상 ACTIVE다.
        this.textbookStatus = ReportTextbookStatus.ACTIVE;
        this.sortOrder = sortOrder;
    }

    /***
     * 함수 기능: 교재 한 개의 진도 표 줄을 만든다. 교재 이름은 지금 값을 복사해 둔다.
     * @param report 리포트
     * @param textbook 교재
     * @param startLabel 첫 진도 라벨
     * @param endLabel 마지막 진도 라벨
     * @param sortOrder 진도 표 안의 순서
     * @return ACTIVE 진도 행
     */
    public static SettlementReportProgress create(SettlementReport report, Textbook textbook, String startLabel,
                                                  String endLabel, int sortOrder) {
        return new SettlementReportProgress(report, textbook, startLabel, endLabel, sortOrder);
    }
}
