package com.sudam.domain.participant.entity;

import com.sudam.domain.participant.enums.NotificationConsent;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.participant.enums.ParticipantRole;
import com.sudam.domain.participant.enums.ReceiveMethod;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.user.entity.User;
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
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * 과외에 속한 학부모 · 학생. 계정(users)과 분리하고 앱 가입 때(P1) user만 연결한다.
 * 카드마다 같은 링크 주소(/l/{token}/…)를 넣어야 해서 링크 토큰은 원문으로 저장한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "participants",
        indexes = @Index(name = "idx_participants_tutoring_role", columnList = "tutoring_id, role"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_participants_tutoring_user",
                columnNames = {"tutoring_id", "user_id"}
        )
)
public class Participant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 앱 가입 시 연결 (P1).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutoring_id", nullable = false)
    private Tutoring tutoring;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipantRole role;

    // 학부모만.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ParticipantLabel label;

    // 학부모 필수 · 학생 선택 (앱 검증). 삭제 시 NULL.
    @Column(length = 11)
    private String phone;

    // 재발급은 덮어쓰기, 폐기는 NULL.
    @Column(length = 64, unique = true)
    private String linkToken;

    private LocalDateTime linkIssuedAt;

    // 진행 중에는 NULL. 수업 종료 시 종료일 + 30일.
    private LocalDateTime linkExpiresAt;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'LINK'")
    @Column(nullable = false, length = 20)
    private ReceiveMethod receiveMethod;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PENDING'")
    @Column(nullable = false, length = 20)
    private NotificationConsent notificationConsent;

    private LocalDateTime notificationConsentAt;

    // 자녀 알림 동의 (STUDENT 행만).
    private LocalDateTime guardianConsentAt;

    // 동의한 학부모 (STUDENT 행만).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_consent_by")
    private Participant guardianConsentBy;

    // 논리 삭제 시각.
    private LocalDateTime removedAt;

    private Participant(Tutoring tutoring, ParticipantRole role, ParticipantLabel label, String phone) {
        this.tutoring = tutoring;
        this.role = role;
        this.label = label;
        this.phone = phone;
        this.receiveMethod = ReceiveMethod.LINK;
        this.notificationConsent = NotificationConsent.PENDING;
    }

    /***
     * 함수 기능: 학부모 참여자를 만든다. 호칭을 고르지 않으면 보호자님(GUARDIAN)으로 둔다 (2.4.1).
     * @param tutoring 소속 과외
     * @param label 호칭. NULL이면 GUARDIAN
     * @param phone 휴대폰 번호 (숫자만)
     * @return 저장 전 Participant
     */
    public static Participant createParent(Tutoring tutoring, ParticipantLabel label, String phone) {
        return new Participant(tutoring, ParticipantRole.PARENT,
                label == null ? ParticipantLabel.GUARDIAN : label, phone);
    }

    /***
     * 함수 기능: 학생 참여자를 만든다. 학생은 호칭이 없고 번호는 선택이다.
     * @param tutoring 소속 과외
     * @param phone 휴대폰 번호 (숫자만, NULL 가능)
     * @return 저장 전 Participant
     */
    public static Participant createStudent(Tutoring tutoring, String phone) {
        return new Participant(tutoring, ParticipantRole.STUDENT, null, phone);
    }

    /***
     * 함수 기능: 링크 토큰을 발급 · 재발급한다. 재발급이면 이전 토큰을 덮어써 더 이상 열리지 않게 한다.
     *          토큰은 SecureRandom 32바이트를 URL 안전 문자열로 만든 값을 받는다 (ERD 공통 규칙).
     * @param linkToken 새 토큰 원문
     * @param issuedAt 발급 시각
     */
    public void issueLinkToken(String linkToken, LocalDateTime issuedAt) {
        this.linkToken = linkToken;
        this.linkIssuedAt = issuedAt;
    }

    /***
     * 함수 기능: 링크 토큰을 폐기한다. 이후 그 토큰으로는 참여자를 찾을 수 없다.
     */
    public void revokeLinkToken() {
        this.linkToken = null;
    }

    /***
     * 함수 기능: 참여자를 논리 삭제한다. 링크 토큰을 폐기하고 연락처를 파기한다 (ERD 공통 규칙).
     * @param removedAt 삭제 시각
     */
    public void remove(LocalDateTime removedAt) {
        this.removedAt = removedAt;
        this.linkToken = null;
        this.phone = null;
    }
}
