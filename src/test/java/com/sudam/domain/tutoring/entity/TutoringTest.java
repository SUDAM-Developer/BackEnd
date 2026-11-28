package com.sudam.domain.tutoring.entity;

import com.sudam.domain.tutoring.enums.TutoringStatus;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
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

    @Test
    @DisplayName("과목 표시 이름은 선택 순서와 관계없이 과목 순서대로 ' · '로 잇는다")
    void 과목_표시_이름은_과목_순서대로_잇는다() {
        // given
        Tutoring tutoring = tutoring(List.of(Subject.MATH, Subject.ENGLISH), null);

        // when
        String names = tutoring.subjectNames();

        // then
        assertThat(names).isEqualTo("영어 · 수학");
    }

    @Test
    @DisplayName("기타 과목은 직접 입력한 과목명으로 표시한다")
    void 기타_과목은_직접_입력한_이름으로_표시한다() {
        // given
        Tutoring tutoring = tutoring(List.of(Subject.OTHER, Subject.ENGLISH), "논술");

        // when
        String names = tutoring.subjectNames();

        // then
        assertThat(names).isEqualTo("영어 · 논술");
    }

    @ParameterizedTest(name = "{0}: {1}학년({2}학년도 기준) → {3} 조회 시 {4}")
    @CsvSource({
            "같은 학년도,             10, 2026, 2026-11-27, 10",
            "2월은 아직 지난 학년도,  10, 2026, 2027-02-28, 10",
            "3월에 한 학년 오름,      10, 2026, 2027-03-01, 11",
            "고3을 넘으면 12로 고정,  10, 2026, 2029-03-01, 12",
            "기타(0)는 오르지 않음,    0, 2026, 2028-03-01, 0"
    })
    @DisplayName("현재 학년은 학년도(3월 시작)마다 하나씩 오르고, 기타는 그대로, 고3을 넘으면 12다")
    void 현재_학년_계산(String situation, int gradeLevel, int gradeSchoolYear, LocalDate date, int expected) {
        // given
        Tutoring tutoring = Tutoring.builder()
                .teacher(TEACHER)
                .subjects(List.of(Subject.MATH))
                .studentName("김민준")
                .gradeLevel(gradeLevel)
                .gradeSchoolYear(gradeSchoolYear)
                .build();

        // when
        int current = tutoring.currentGradeLevel(date);

        // then
        assertThat(current).isEqualTo(expected);
    }

    private static Tutoring tutoring(List<Subject> subjects, String subjectOther) {
        return Tutoring.builder()
                .teacher(TEACHER)
                .subjects(subjects)
                .subjectOther(subjectOther)
                .studentName("김민준")
                .gradeLevel(8)
                .gradeSchoolYear(2026)
                .build();
    }

    private static Tutoring tutoring() {
        return Tutoring.builder()
                .teacher(TEACHER)
                .subjects(List.of(Subject.MATH))
                .studentName("김민준")
                .gradeLevel(8)
                .gradeSchoolYear(2026)
                .build();
    }
}
