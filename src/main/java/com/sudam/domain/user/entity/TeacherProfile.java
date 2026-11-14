package com.sudam.domain.user.entity;

import com.sudam.domain.user.enums.Subject;
import com.sudam.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "teacher_profiles")
public class TeacherProfile extends BaseTimeEntity {

    // PK = users.id. 회원당 프로필 1개.
    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 선생님 이름
    @Column(nullable = false, length = 10)
    private String name;

    // subjects에 OTHER가 있을 때만 값이 있다. 과외의 subject_other와는 별개 값이다.
    @Column(length = 20)
    private String subjectOther;

    // 과목은 별도 테이블 없이 JSON 배열(["ENGLISH","MATH"])로 한 컬럼에 저장한다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Set<Subject> subjects;

    private TeacherProfile(User user, String name, Collection<Subject> subjects, String subjectOther) {
        this.user = user;
        update(name, subjects, subjectOther);
    }

    public static TeacherProfile create(User user, String name, Collection<Subject> subjects, String subjectOther) {
        return new TeacherProfile(user, name, subjects, subjectOther);
    }

    /***
     * 함수 기능: 이름·과목·기타 과목명을 전체 교체한다 (PUT 의미).
     * @param subjectOther OTHER가 없으면 호출자가 null로 정규화해서 넘긴다
     */
    public void update(String name, Collection<Subject> subjects, String subjectOther) {
        this.name = name;
        this.subjects = EnumSet.copyOf(subjects);
        this.subjectOther = subjectOther;
    }
}
