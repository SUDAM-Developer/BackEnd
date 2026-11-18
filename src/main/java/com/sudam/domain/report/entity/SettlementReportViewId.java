package com.sudam.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/**
 * 리포트 열람의 복합 키 (report_id, participant_id). 참여자 한 명은 리포트 하나에 첫 열람 기록을 하나만 가진다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementReportViewId implements Serializable {

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "participant_id")
    private Long participantId;

    SettlementReportViewId(Long reportId, Long participantId) {
        this.reportId = reportId;
        this.participantId = participantId;
    }

    // 복합 키는 영속성 컨텍스트가 값으로 비교하므로 두 ID로 직접 구현한다.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SettlementReportViewId other)) {
            return false;
        }
        return Objects.equals(reportId, other.reportId) && Objects.equals(participantId, other.participantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reportId, participantId);
    }
}
