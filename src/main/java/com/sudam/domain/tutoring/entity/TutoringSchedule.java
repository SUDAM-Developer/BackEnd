package com.sudam.domain.tutoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 적용 기간이 있는 반복 수업 규칙. 예정 회차는 저장하지 않고 이 규칙으로 매번 계산한다.
 * 요일을 바꾸면 기존 행의 effective_to를 채우고 새 행을 추가한다 (P1).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "tutoring_schedules",
        indexes = @Index(name = "idx_tutoring_schedules_tutoring_effective_to", columnList = "tutoring_id, effective_to")
)
public class TutoringSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutoring_id", nullable = false)
    private Tutoring tutoring;

    // 1(월)~7(일). java.time.DayOfWeek#getValue와 같은 값.
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer dayOfWeek;

    // 10분 단위, 하루 안에서만 (앱 검증).
    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    // NULL = 현재 적용 중.
    private LocalDate effectiveTo;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private TutoringSchedule(Tutoring tutoring, Integer dayOfWeek, LocalTime startTime, LocalTime endTime,
                             LocalDate effectiveFrom) {
        this.tutoring = tutoring;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.effectiveFrom = effectiveFrom;
    }
}
