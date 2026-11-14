package com.sudam.domain.user.entity;

import com.sudam.domain.user.enums.TermsCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 약관 행은 코드에서 생성하지 않는다(직접 DB에 입력 및 업데이트).
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "terms",
        uniqueConstraints = @UniqueConstraint(columnNames = {"code", "version"})
)
public class Terms {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TermsCode code;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private boolean isRequired;

    // 시행 시각. 이 시각이 지난 행 중 code별 최신 version이 현재 약관이다.
    @Column(nullable = false)
    private LocalDateTime effectiveAt;
}
