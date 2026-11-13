package com.sudam.domain.user.entity;

import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TeacherProfile 엔티티 상태 변경")
class TeacherProfileTest {

    private final User user = User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app");

    @Test
    @DisplayName("생성 시 과목 목록의 중복은 제거되어 저장된다")
    void 생성_시_과목_중복_제거() {
        // when
        TeacherProfile profile = TeacherProfile.create(user, "한서윤",
                List.of(Subject.MATH, Subject.MATH, Subject.OTHER), "논술");

        // then
        assertThat(profile.getName()).isEqualTo("한서윤");
        assertThat(profile.getSubjects()).containsExactlyInAnyOrder(Subject.MATH, Subject.OTHER);
        assertThat(profile.getSubjectOther()).isEqualTo("논술");
    }

    @Test
    @DisplayName("수정하면 이름 · 과목 · 기타 과목명이 전체 교체된다")
    void 수정_시_전체_교체() {
        // given
        TeacherProfile profile = TeacherProfile.create(user, "한서윤",
                List.of(Subject.ENGLISH, Subject.OTHER), "논술");

        // when — OTHER를 빼면 호출자(서비스)가 subjectOther를 null로 넘긴다
        profile.update("김민준", List.of(Subject.MATH), null);

        // then
        assertThat(profile.getName()).isEqualTo("김민준");
        assertThat(profile.getSubjects()).containsExactly(Subject.MATH);
        assertThat(profile.getSubjectOther()).isNull();
    }
}
