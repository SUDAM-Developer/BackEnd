package com.sudam.domain.session.entity;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 수업 회차. 예정 회차는 저장하지 않고 반복 일정(tutoring_schedules)으로 계산하며,
 * 기록 · 휴강 · 보강처럼 "무언가 일어난" 회차만 이 테이블에 남는다 (ERD 5. 수업 도메인).
 *
 * <p>P0 기록 API 범위의 컬럼만 둔다. 휴강(cancel_reason · canceled_at) · 시간 변경(time_changed_at)은 그 기능에서 추가한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sessions_schedule_original_date",
                columnNames = {"schedule_id", "original_date"}
        ),
        indexes = {
                @Index(name = "idx_sessions_tutoring_date", columnList = "tutoring_id, session_date"),
                @Index(name = "idx_sessions_cycle_recognized_date", columnList = "cycle_id, is_recognized, session_date")
        }
)
public class Session extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutoring_id", nullable = false)
    private Tutoring tutoring;

    // 소속 사이클. 기록(COMPLETED)은 저장 시점의 진행 중 사이클, 미인정 휴강 · 예정 행은 NULL이고 소속은 날짜로 계산한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id")
    private Cycle cycle;

    // 반복 일정에서 나온 회차만 값이 있다. 보강 · 추가 회차는 NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private TutoringSchedule schedule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionType sessionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    // 실제 수업 날짜.
    @Column(nullable = false)
    private LocalDate sessionDate;

    // 시간을 바꾼 회차만 값이 있다 (수업 길이는 그대로).
    private LocalTime startTime;

    private LocalTime endTime;

    // 반복 일정상 원래 날짜. 정규 회차 기록은 sessionDate와 같다.
    private LocalDate originalDate;

    // 인정 회차 여부. 기록은 TRUE, 휴강은 정책에 따라, 예정 행은 NULL.
    private Boolean isRecognized;

    private Session(Tutoring tutoring, Cycle cycle, TutoringSchedule schedule, SessionType sessionType,
                    SessionStatus status, LocalDate sessionDate, LocalDate originalDate, Boolean isRecognized) {
        this.tutoring = tutoring;
        this.cycle = cycle;
        this.schedule = schedule;
        this.sessionType = sessionType;
        this.status = status;
        this.sessionDate = sessionDate;
        this.originalDate = originalDate;
        this.isRecognized = isRecognized;
    }

    /***
     * 함수 기능: 반복 일정의 회차를 기록 완료 상태로 만든다 (4.1.1). 정규 회차의 기록은 항상 인정 회차다.
     * @param tutoring 과외
     * @param cycle 저장 시점의 진행 중 사이클 (호출 전에 과외 행을 잠그고 조회한 것)
     * @param schedule 회차가 속한 반복 일정
     * @param originalDate 반복 일정상 수업 날짜 (= 실제 수업 날짜)
     * @return REGULAR · COMPLETED · 인정 회차
     */
    public static Session completeRegular(Tutoring tutoring, Cycle cycle, TutoringSchedule schedule,
                                          LocalDate originalDate) {
        return new Session(tutoring, cycle, schedule, SessionType.REGULAR, SessionStatus.COMPLETED,
                originalDate, originalDate, true);
    }
}
