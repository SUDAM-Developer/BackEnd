package com.sudam.domain.user.entity;

import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User 엔티티 상태 변경")
class UserTest {

    @Test
    @DisplayName("소셜 로그인으로 처음 들어온 회원은 선생님으로 생성된다")
    void 선생님으로_생성된다() {
        // when
        User user = User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app");

        // then
        assertThat(user.getRole()).isEqualTo(Role.TEACHER);
        assertThat(user.getProvider()).isEqualTo(Provider.KAKAO);
        assertThat(user.getProviderId()).isEqualTo("kakao-1");
        assertThat(user.getEmail()).isEqualTo("teacher@sudam.app");
        assertThat(user.getWithdrawnAt()).isNull();
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("탈퇴하면 탈퇴 시각이 기록되고 이메일·소셜 식별값이 파기된다")
    void 탈퇴하면_개인정보가_파기된다() {
        // given
        User user = User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app");

        // when
        user.withdraw();

        // then
        assertThat(user.getWithdrawnAt()).isNotNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getProviderId()).isNull();
    }

    @Test
    @DisplayName("탈퇴해도 권한과 소셜 공급자는 남는다")
    void 탈퇴해도_권한과_공급자는_유지된다() {
        // given
        User user = User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app");

        // when
        user.withdraw();

        // then — provider_id만 비워 같은 계정으로 재가입이 가능하게 한다
        assertThat(user.getRole()).isEqualTo(Role.TEACHER);
        assertThat(user.getProvider()).isEqualTo(Provider.KAKAO);
    }
}
