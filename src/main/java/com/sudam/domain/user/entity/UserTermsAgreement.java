package com.sudam.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "user_terms_agreements",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "terms_id"})
)
public class UserTermsAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 버전별 약관 행을 가리킨다. 새 버전이 시행되면 그 버전에 다시 동의해야 한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_id", nullable = false)
    private Terms terms;

    @Column(nullable = false)
    private LocalDateTime agreedAt;

    private UserTermsAgreement(User user, Terms terms) {
        this.user = user;
        this.terms = terms;
        this.agreedAt = LocalDateTime.now();
    }

    public static UserTermsAgreement agree(User user, Terms terms) {
        return new UserTermsAgreement(user, terms);
    }
}
