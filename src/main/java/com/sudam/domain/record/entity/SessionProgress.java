package com.sudam.domain.record.entity;

import com.sudam.domain.session.entity.Session;
import com.sudam.domain.tutoring.entity.Textbook;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회차별 교재 진도. [안 함]인 교재는 행을 만들지 않는다.
 * 교재의 현재 진도는 textbooks에 다시 쓰지 않고, 가장 최근 회차의 endPosition으로 계산한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "session_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_session_progress_session_textbook",
                columnNames = {"session_id", "textbook_id"}
        )
)
public class SessionProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기록이 아니라 회차를 가리킨다 (ERD session_progress.session_id).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "textbook_id", nullable = false)
    private Textbook textbook;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private Integer startPosition;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private Integer endPosition;

    private SessionProgress(Session session, Textbook textbook, int startPosition, int endPosition) {
        this.session = session;
        this.textbook = textbook;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    public static SessionProgress create(Session session, Textbook textbook, int startPosition, int endPosition) {
        return new SessionProgress(session, textbook, startPosition, endPosition);
    }
}
