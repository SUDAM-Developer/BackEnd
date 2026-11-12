package com.sudam.domain.user.entity;

import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Role;
import com.sudam.global.entity.BaseTimeEntity;
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

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"})
)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // 수담 운영자. 관리자 API 권한이며 값은 DB에서 직접 켠다.
    @Column(nullable = false)
    private boolean isAdmin;

    // 탈퇴 시 NULL.
    @Column(unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    // 카카오 회원번호. 탈퇴 시 NULL.
    private String providerId;

    // 논리 삭제 시각. 탈퇴 처리는 TODO
    private LocalDateTime withdrawnAt;

    private User(Role role, Provider provider, String providerId, String email) {
        this.role = role;
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
    }

    /***
     * 함수 기능: 소셜 로그인으로 처음 들어온 회원을 선생님으로 생성한다.
     *          웹 버전은 가입하면 TEACHER로 자동 저장된다 (역할 선택은 P1).
     * @param provider 소셜 공급자
     * @param providerId 공급자가 부여한 회원 식별자
     * @param email 공급자가 제공한 이메일
     * @return 저장 전 User 엔티티
     */
    public static User createTeacher(Provider provider, String providerId, String email) {
        return new User(Role.TEACHER, provider, providerId, email);
    }
}
