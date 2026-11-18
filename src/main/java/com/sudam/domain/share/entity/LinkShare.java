package com.sudam.domain.share.entity;

import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.share.enums.ShareChannel;
import com.sudam.domain.share.enums.ShareKind;
import com.sudam.domain.share.enums.ShareSentVia;
import com.sudam.domain.share.enums.ShareSourceType;
import com.sudam.domain.share.enums.ShareStatus;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.user.entity.TeacherProfile;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 공유 기록 (ERD link_shares). 선생님이 카카오톡 공유 창을 열 때 REQUESTED로 만들고,
 * 보낸 것이 확인되면 SENT가 된다. 학부모 · 학생에게 가는 메시지는 모두 선생님의 카톡 공유로 나간다.
 * 웹훅(chat_type · hash_chat_id)은 아직 받지 않아 UT에서는 선생님 확인(MANUAL)으로만 SENT가 된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "link_shares",
        indexes = {
                @Index(name = "idx_link_shares_tutoring_kind_requested", columnList = "tutoring_id, kind, requested_at"),
                @Index(name = "idx_link_shares_source", columnList = "source_type, source_id"),
                @Index(name = "idx_link_shares_teacher_status_sent", columnList = "teacher_id, status, sent_at")
        }
)
public class LinkShare {

    // 카카오 공유 serverCallbackArgs에 실어 보낼 발송 ID.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    // 상담 단계 수업 계획서는 과외 · 참여자가 없다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutoring_id")
    private Tutoring tutoring;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private Participant participant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShareKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShareChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ShareSourceType sourceType;

    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShareStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ShareSentVia sentVia;

    // 웹훅 CHAT_TYPE · HASH_CHAT_ID (웹훅과 함께 채운다).
    @Column(length = 20)
    private String chatType;

    @Column(length = 100)
    private String hashChatId;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime sentAt;

    private LinkShare(Participant participant, ShareKind kind, ShareSourceType sourceType, Long sourceId,
                      LocalDateTime requestedAt) {
        this.tutoring = participant.getTutoring();
        this.teacher = participant.getTutoring().getTeacher();
        this.participant = participant;
        this.kind = kind;
        this.channel = ShareChannel.KAKAO_SHARE;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.status = ShareStatus.REQUESTED;
        this.requestedAt = requestedAt;
    }

    /***
     * 함수 기능: 선생님이 카카오톡 공유 창을 열 때 공유 기록을 REQUESTED로 만든다.
     * @param participant 받는 사람 (과외 · 선생님을 함께 조회한 것)
     * @param kind        공유 종류
     * @param sourceType  원인 데이터 종류
     * @param sourceId    원인 데이터 ID
     * @param requestedAt 공유 창을 연 시각
     * @return 저장 전 LinkShare
     */
    public static LinkShare request(Participant participant, ShareKind kind, ShareSourceType sourceType,
                                    Long sourceId, LocalDateTime requestedAt) {
        return new LinkShare(participant, kind, sourceType, sourceId, requestedAt);
    }
}
