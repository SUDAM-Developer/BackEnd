package com.sudam.domain.tutoring.entity;

import com.sudam.domain.tutoring.enums.TutoringStatus;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tutoring 엔티티 상태 변경")
class TutoringTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);
    private static final TeacherProfile TEACHER = TeacherProfile.create(
            User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app"), "한서윤", List.of(Subject.MATH), null);

    @Test
    @DisplayName("과외는 진행 중 상태로 생성된다")
    void 진행_중으로_생성된다() {
        // when
        Tutoring tutoring = tutoring();

        // then
        assertThat(tutoring.getStatus()).isEqualTo(TutoringStatus.ACTIVE);
        assertThat(tutoring.getEndedAt()).isNull();
        assertThat(tutoring.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("수업을 종료하면 종료 상태가 되고 종료 시각이 기록된다")
    void 종료하면_상태와_시각이_기록된다() {
        // given
        Tutoring tutoring = tutoring();

        // when
        tutoring.end(NOW);

        // then
        assertThat(tutoring.getStatus()).isEqualTo(TutoringStatus.ENDED);
        assertThat(tutoring.getEndedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("과외를 삭제하면 삭제 시각이 기록되고 학생 이름이 파기된다")
    void 삭제하면_학생_이름이_파기된다() {
        // given
        Tutoring tutoring = tutoring();

        // when
        tutoring.delete(NOW);

        // then
        assertThat(tutoring.isDeleted()).isTrue();
        assertThat(tutoring.getDeletedAt()).isEqualTo(NOW);
        assertThat(tutoring.getStudentName()).isNull();
        assertThat(tutoring.getTeacher()).isSameAs(TEACHER);
    }

    private static Tutoring tutoring() {
        return Tutoring.builder()
                .teacher(TEACHER)
                .subject(Subject.MATH)
                .studentName("김민준")
                .gradeLevel(8)
                .gradeSchoolYear(2026)
                .build();
    }
}
