package com.sudam.global.security.util;

import com.sudam.domain.auth.exception.AuthException;
import com.sudam.domain.user.enums.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtUtil 토큰 발급·검증")
class JwtUtilTest {

    // HS256은 256비트 이상 키를 요구하므로 32바이트 이상으로 둔다.
    private static final String SECRET = "sudam-test-secret-key-for-jwt-unit-test-0123456789";
    private static final long ACCESS_TOKEN_EXPIRATION = 1_800_000L;
    private static final long REFRESH_TOKEN_EXPIRATION = 1_209_600_000L;

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);

    @Test
    @DisplayName("액세스 토큰을 두 번 발급하면 jti가 서로 다르다")
    void 액세스_토큰의_jti는_발급마다_다르다() {
        // given & when
        Claims first = jwtUtil.parseAccessToken(jwtUtil.createAccessToken(1L, Role.TEACHER));
        Claims second = jwtUtil.parseAccessToken(jwtUtil.createAccessToken(1L, Role.TEACHER));

        // then
        assertThat(first.getId()).isNotBlank();
        assertThat(second.getId()).isNotBlank();
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    @DisplayName("리프레시 토큰을 같은 회원에게 연속 발급해도 토큰 값이 서로 다르다")
    void 리프레시_토큰은_연속_발급해도_값이_다르다() {
        // given & when — iat·exp는 초 단위라 jti가 없으면 같은 초에 발급한 토큰이 동일해진다
        String first = jwtUtil.createRefreshToken(1L, Role.TEACHER);
        String second = jwtUtil.createRefreshToken(1L, Role.TEACHER);

        // then
        assertThat(first).isNotEqualTo(second);
        assertThat(jwtUtil.parseRefreshToken(first).getId())
                .isNotEqualTo(jwtUtil.parseRefreshToken(second).getId());
    }

    @Test
    @DisplayName("액세스 토큰에는 userId·권한·종류가 담긴다")
    void 액세스_토큰_클레임() {
        // when
        Claims claims = jwtUtil.parseAccessToken(jwtUtil.createAccessToken(7L, Role.TEACHER));

        // then
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(jwtUtil.getRole(claims)).isEqualTo(Role.TEACHER);
    }

    @Test
    @DisplayName("리프레시 토큰을 액세스 토큰으로 파싱하면 AUTH401_3 예외가 발생한다")
    void 리프레시_토큰을_액세스_자리에_넣으면_예외() {
        // given
        String refreshToken = jwtUtil.createRefreshToken(1L, Role.TEACHER);

        // when & then
        assertThatThrownBy(() -> jwtUtil.parseAccessToken(refreshToken))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode().getCode())
                .isEqualTo("AUTH401_3");
    }

    @Test
    @DisplayName("액세스 토큰을 리프레시 토큰으로 파싱하면 AUTH401_3 예외가 발생한다")
    void 액세스_토큰을_리프레시_자리에_넣으면_예외() {
        // given
        String accessToken = jwtUtil.createAccessToken(1L, Role.TEACHER);

        // when & then
        assertThatThrownBy(() -> jwtUtil.parseRefreshToken(accessToken))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode().getCode())
                .isEqualTo("AUTH401_3");
    }

    @Test
    @DisplayName("토큰이 null이면 AUTH401_3 예외가 발생한다")
    void 토큰이_null이면_예외() {
        // when & then — 쿠키가 없을 때 null이 그대로 들어온다
        assertThatThrownBy(() -> jwtUtil.parseRefreshToken(null))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode().getCode())
                .isEqualTo("AUTH401_3");
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 AUTH401_3 예외가 발생한다")
    void 서명이_다르면_예외() {
        // given
        JwtUtil other = new JwtUtil(
                "another-secret-key-that-is-long-enough-0123456789",
                ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
        String forged = other.createAccessToken(1L, Role.TEACHER);

        // when & then
        assertThatThrownBy(() -> jwtUtil.parseAccessToken(forged))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode().getCode())
                .isEqualTo("AUTH401_3");
    }

    @Test
    @DisplayName("남은 유효 기간은 0보다 크고 설정한 만료 시간 이하다")
    void 남은_유효_기간() {
        // given
        Claims claims = jwtUtil.parseAccessToken(jwtUtil.createAccessToken(1L, Role.TEACHER));

        // when
        Duration remaining = jwtUtil.getRemainingExpiration(claims);

        // then
        assertThat(remaining).isPositive()
                .isLessThanOrEqualTo(Duration.ofMillis(ACCESS_TOKEN_EXPIRATION));
    }
}
