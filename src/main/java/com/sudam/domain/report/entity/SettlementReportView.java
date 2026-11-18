package com.sudam.domain.report.entity;

import com.sudam.domain.participant.entity.Participant;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 리포트 열람 (참여자별 첫 열람). 행이 있으면 그 참여자가 읽은 것이고, 학부모가 둘이면 한 명이라도 읽으면 읽음이다.
 * 첫 열람 시각만 남기므로 다시 열어도 바꾸지 않는다. 기록은 학부모 열람 API와 함께 쌓는다 (UT 시점에는 없음).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "settlement_report_views")
public class SettlementReportView {

    @EmbeddedId
    private SettlementReportViewId id;

    @MapsId("reportId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id")
    private SettlementReport report;

    @MapsId("participantId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id")
    private Participant participant;

    @Column(nullable = false)
    private LocalDateTime firstViewedAt;

    private SettlementReportView(SettlementReport report, Participant participant, LocalDateTime firstViewedAt) {
        this.id = new SettlementReportViewId(report.getId(), participant.getId());
        this.report = report;
        this.participant = participant;
        this.firstViewedAt = firstViewedAt;
    }

    /***
     * 함수 기능: 참여자의 첫 열람을 기록한다. 이미 기록이 있으면 호출하지 않는다 (첫 열람 시각을 유지).
     * @param report 열람한 정산 리포트
     * @param participant 열람한 참여자
     * @param viewedAt 열람 시각
     * @return 리포트 열람
     */
    public static SettlementReportView firstView(SettlementReport report, Participant participant,
                                                 LocalDateTime viewedAt) {
        return new SettlementReportView(report, participant, viewedAt);
    }
}
