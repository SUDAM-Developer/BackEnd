package com.sudam.domain.cycle.entity;

import com.sudam.domain.cycle.enums.CycleStatus;
import com.sudam.domain.cycle.exception.CycleException;
import com.sudam.domain.cycle.exception.code.CycleErrorCode;
import com.sudam.domain.tutoring.entity.Tutoring;
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

import java.time.LocalDateTime;

/**
 * 수업 사이클 = 정산 회차만큼의 수업 묶음. 정산하면 SETTLED가 되고 다음 사이클이 열린다.
 * 회차(sessions.cycle_id)가 이 엔티티를 참조한다.
 *
 * <p>'진행 중 사이클은 과외당 1개'(ERD의 in_progress_key 생성 열)는 ddl-auto로 만들어지지 않아 DB로 보장하지 않는다.
 * 사이클을 열고 닫는 모든 쪽 — 과외 만들기 · 정산 실행 · 수업 종료 · 수업료 변경 — 은
 * 먼저 {@code TutoringRepository.findByIdForUpdate}로 과외 행을 잠근 뒤 진행 중 사이클을 조회 · 변경한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "cycles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cycles_tutoring_cycle_no",
                columnNames = {"tutoring_id", "cycle_no"}
        )
)
public class Cycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutoring_id", nullable = false)
    private Tutoring tutoring;

    // 1부터. 과외 안에서 유일하다.
    @Column(nullable = false)
    private Integer cycleNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CycleStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime settledAt;

    private Cycle(Tutoring tutoring, int cycleNo, LocalDateTime startedAt) {
        this.tutoring = tutoring;
        this.cycleNo = cycleNo;
        this.status = CycleStatus.IN_PROGRESS;
        this.startedAt = startedAt;
    }

    /***
     * 함수 기능: 진행 중 사이클을 연다. 호출 전에 과외 행을 잠그고 진행 중 사이클이 없는지 확인한다.
     * @param tutoring 과외
     * @param cycleNo 사이클 번호 (1부터)
     * @param startedAt 사이클이 열린 시각
     * @return IN_PROGRESS 사이클
     */
    public static Cycle open(Tutoring tutoring, int cycleNo, LocalDateTime startedAt) {
        return new Cycle(tutoring, cycleNo, startedAt);
    }

    /***
     * 함수 기능: 사이클을 정산 완료로 바꾼다 (6.1.1).
     * @param settledAt 정산 실행 시각
     * @throws CycleException 이미 정산된 사이클이면 CYCLE409_1
     */
    public void settle(LocalDateTime settledAt) {
        if (!isInProgress()) {
            throw new CycleException(CycleErrorCode.ALREADY_SETTLED);
        }
        this.status = CycleStatus.SETTLED;
        this.settledAt = settledAt;
    }

    public boolean isInProgress() {
        return status == CycleStatus.IN_PROGRESS;
    }
}
