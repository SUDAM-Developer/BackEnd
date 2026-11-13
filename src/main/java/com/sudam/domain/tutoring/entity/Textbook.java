package com.sudam.domain.tutoring.entity;

import com.sudam.domain.tutoring.enums.ProgressUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 과외에서 쓰는 교재 (과외당 1~5권, 앱 검증). 교재 내용 · 목차는 저작권 때문에 저장하지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "textbooks")
public class Textbook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutoring_id", nullable = false)
    private Tutoring tutoring;

    @Column(nullable = false, length = 20)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProgressUnit progressUnit;

    // progressUnit = CUSTOM일 때 단위 이름 1~5자 (예: 강, 과, 세트).
    @Column(length = 5)
    private String customUnitLabel;

    // 지금 진도. 비우면 0이고 첫 기록 제안 칩은 1부터 (2.1.2).
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @ColumnDefault("0")
    @Column(nullable = false)
    private Integer startPosition;

    // 사용 종료 (P1).
    private LocalDateTime endedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Textbook(Tutoring tutoring, String name, ProgressUnit progressUnit, String customUnitLabel,
                     Integer startPosition) {
        this.tutoring = tutoring;
        this.name = name;
        this.progressUnit = progressUnit;
        this.customUnitLabel = customUnitLabel;
        this.startPosition = startPosition == null ? 0 : startPosition;
    }
}
