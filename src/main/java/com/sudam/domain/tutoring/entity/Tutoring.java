package com.sudam.domain.tutoring.entity;

import com.sudam.domain.tutoring.enums.TutoringStatus;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.enums.Subject;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 과외 1개 = 학생 1명.
 * 수업료 · 정산 회차 등은 사이클 기준 이력(TutoringPolicy)에, 수업 요일은 TutoringSchedule에 둔다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "tutorings",
        indexes = @Index(name = "idx_tutorings_teacher_status", columnList = "teacher_id, status")
)
public class Tutoring extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 선생님 프로필. teacher_profiles의 PK가 users.id라 teacher_id 값은 그대로 users.id다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherProfile teacher;

    // 과목 코드. 프로필과 같은 Subject enum을 쓰고 테이블 없이 enum 이름으로 저장한다 (ERD 공통 규칙).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Subject subject;

    // 과목 '기타' 직접 입력 1~10자. subject = OTHER일 때만 값이 있다 (앱 검증).
    @Column(length = 10)
    private String subjectOther;

    // 삭제 시 NULL.
    @Column(length = 10)
    private String studentName;

    // 1~12 = 초1~고3, 0 = 기타.
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer gradeLevel;

    // 학년 '기타' 직접 입력 (예: 재수생, 성인).
    @Column(length = 10)
    private String gradeOther;

    // 학년 기준 학년도. 현재 학년 = gradeLevel + (올해 학년도 - gradeSchoolYear).
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private Integer gradeSchoolYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TutoringStatus status;

    private LocalDateTime endedAt;

    // 논리 삭제 시각.
    private LocalDateTime deletedAt;

    @Builder
    private Tutoring(TeacherProfile teacher, Subject subject, String subjectOther, String studentName,
                     Integer gradeLevel, String gradeOther, Integer gradeSchoolYear) {
        this.teacher = teacher;
        this.subject = subject;
        this.subjectOther = subjectOther;
        this.studentName = studentName;
        this.gradeLevel = gradeLevel;
        this.gradeOther = gradeOther;
        this.gradeSchoolYear = gradeSchoolYear;
        this.status = TutoringStatus.ACTIVE;
    }

    /***
     * 함수 기능: 수업을 종료한다 (7.1.2). 종료 상태 검증은 서비스에서 한다.
     * @param endedAt 종료 시각
     */
    public void end(LocalDateTime endedAt) {
        this.status = TutoringStatus.ENDED;
        this.endedAt = endedAt;
    }

    /***
     * 함수 기능: 과외를 논리 삭제하고 학생 이름을 파기한다 (ERD 공통 규칙).
     * @param deletedAt 삭제 시각
     */
    public void delete(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        this.studentName = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
