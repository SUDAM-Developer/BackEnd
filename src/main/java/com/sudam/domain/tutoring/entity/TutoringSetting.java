package com.sudam.domain.tutoring.entity;

import com.sudam.domain.tutoring.enums.ParentUpdateFrequency;
import com.sudam.domain.tutoring.enums.PresetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 과외별 공유 설정 (1:1, PK = tutoring_id).
 * 기록 항목 · 학부모 공개 항목 설정은 P2라 P0 고정값을 쓰고 여기에 저장하지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "tutoring_settings")
public class TutoringSetting {

    @Id
    private Long tutoringId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutoring_id")
    private Tutoring tutoring;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PresetType presetType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParentUpdateFrequency parentUpdateFrequency;

    // 학생 숙제 알림. 받는 사람은 학생만이고 학생 번호가 없으면 켜지 않는다 (앱 검증).
    @Column(nullable = false)
    private boolean homeworkNotifyEnabled;

    // 미입금 알림 (전날 9시, 선생님께).
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean unpaidReminderEnabled;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private TutoringSetting(Tutoring tutoring, PresetType presetType, ParentUpdateFrequency parentUpdateFrequency,
                            boolean homeworkNotifyEnabled, boolean unpaidReminderEnabled) {
        this.tutoring = tutoring;
        this.presetType = presetType;
        this.parentUpdateFrequency = parentUpdateFrequency;
        this.homeworkNotifyEnabled = homeworkNotifyEnabled;
        this.unpaidReminderEnabled = unpaidReminderEnabled;
    }

    /***
     * 함수 기능: 공유 설정을 바꾼다. 화면이 설정 전체를 한 번에 저장하므로 모든 값을 함께 받는다.
     */
    public void change(PresetType presetType, ParentUpdateFrequency parentUpdateFrequency,
                       boolean homeworkNotifyEnabled, boolean unpaidReminderEnabled) {
        this.presetType = presetType;
        this.parentUpdateFrequency = parentUpdateFrequency;
        this.homeworkNotifyEnabled = homeworkNotifyEnabled;
        this.unpaidReminderEnabled = unpaidReminderEnabled;
    }
}
