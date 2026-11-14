package com.sudam.domain.record.entity;

import com.sudam.domain.record.enums.HomeworkResult;
import com.sudam.domain.record.enums.Understanding;
import com.sudam.domain.session.entity.Session;
import com.sudam.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회차 1개의 수업 기록 (회차 1 : 기록 1). 교재 진도는 SessionProgress에 교재별로 따로 둔다.
 * 태도(attitude)는 P2라 컬럼을 두지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "session_records")
public class SessionRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 반대편(Session)에는 매핑을 두지 않는다. 반대편 @OneToOne은 지연 로딩이 되지 않아 회차 조회마다 기록 쿼리가 따라 나간다.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Understanding understanding;

    // 첫 회차이거나 지난 숙제가 없으면 NULL.
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private HomeworkResult previousHomeworkResult;

    @Column(length = 100)
    private String nextHomework;

    // 관찰 한 줄. 근황에는 그대로 공개하고 정산 리포트에는 원문을 보여주지 않는다.
    @Column(length = 100)
    private String observationMemo;

    private SessionRecord(Session session, Understanding understanding, HomeworkResult previousHomeworkResult,
                          String nextHomework, String observationMemo) {
        this.session = session;
        this.understanding = understanding;
        this.previousHomeworkResult = previousHomeworkResult;
        this.nextHomework = nextHomework;
        this.observationMemo = observationMemo;
    }

    public static SessionRecord create(Session session, Understanding understanding,
                                       HomeworkResult previousHomeworkResult,
                                       String nextHomework, String observationMemo) {
        return new SessionRecord(session, understanding, previousHomeworkResult, nextHomework, observationMemo);
    }
}
