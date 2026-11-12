package com.sudam.domain.auth.service;

import com.sudam.domain.auth.dto.TokenPair;
import com.sudam.domain.auth.exception.AuthException;
import com.sudam.domain.user.enums.Role;
import com.sudam.global.security.token.TokenStore;
import com.sudam.global.security.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 토큰 재발급·로그아웃")
class AuthServiceTest {

    private static final String SECRET = "sudam-test-secret-key-for-jwt-unit-test-0123456789";
    private static final long ACCESS_TOKEN_EXPIRATION = 1_800_000L;
    private static final long REFRESH_TOKEN_EXPIRATION = 1_209_600_000L;
    private static final Long USER_ID = 1L;

    @Mock
    private TokenStore tokenStore;

    // 실제 토큰으로 검증해야 클레임 구성까지 함께 확인되므로 JwtUtil은 목으로 대체하지 않는다.
    private JwtUtil jwtUtil;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
        authService = new AuthService(jwtUtil, tokenStore);
    }

    @Test
    @DisplayName("저장된 리프레시 토큰이 없으면 AUTH401_5 예외가 발생한다")
    void 저장된_토큰이_없으면_예외() {
        // given
        String refreshToken = jwtUtil.createRefreshToken(USER_ID, Role.TEACHER);
        given(tokenStore.findRefreshToken(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.reissue(refreshToken))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode().getCode())
                .isEqualTo("AUTH401_5");
    }

    @Test
    @DisplayName("저장된 토큰과 값이 다르면 저장된 토큰을 지우고 AUTH401_6 예외가 발생한다")
    void 저장된_토큰과_다르면_삭제하고_예외() {
        // given — 이미 교체된(쓰고 버린) 토큰이 다시 들어온 상황
        String usedToken = jwtUtil.createRefreshToken(USER_ID, Role.TEACHER);
        String currentToken = jwtUtil.createRefreshToken(USER_ID, Role.TEACHER);
        given(tokenStore.findRefreshToken(USER_ID)).willReturn(Optional.of(currentToken));

        // when & then
        assertThatThrownBy(() -> authService.reissue(usedToken))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode().getCode())
                .isEqualTo("AUTH401_6");

        verify(tokenStore).deleteRefreshToken(USER_ID);
    }

    @Test
    @DisplayName("저장된 토큰과 일치하면 새 토큰을 발급하고 저장소를 갱신한다")
    void 일치하면_재발급한다() {
        // given
        String refreshToken = jwtUtil.createRefreshToken(USER_ID, Role.TEACHER);
        given(tokenStore.findRefreshToken(USER_ID)).willReturn(Optional.of(refreshToken));

        // when
        TokenPair tokens = authService.reissue(refreshToken);

        // then
        assertThat(tokens.refreshToken()).isNotEqualTo(refreshToken);
        assertThat(jwtUtil.parseAccessToken(tokens.accessToken()).getSubject()).isEqualTo("1");
        assertThat(jwtUtil.getRole(jwtUtil.parseAccessToken(tokens.accessToken()))).isEqualTo(Role.TEACHER);
        assertThat(jwtUtil.parseRefreshToken(tokens.refreshToken()).getSubject()).isEqualTo("1");

        verify(tokenStore).saveRefreshToken(USER_ID, tokens.refreshToken());
        verify(tokenStore, never()).deleteRefreshToken(any());
    }

    @Test
    @DisplayName("액세스 토큰을 리프레시 토큰 자리에 보내면 AUTH401_3이고 저장된 토큰은 지우지 않는다")
    void 액세스_토큰을_보내면_저장된_토큰을_지키고_예외() {
        // given
        String accessToken = jwtUtil.createAccessToken(USER_ID, Role.TEACHER);

        // when & then — 종류 검증이 없으면 불일치로 오판해 정상 세션을 끊게 된다
        assertThatThrownBy(() -> authService.reissue(accessToken))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode().getCode())
                .isEqualTo("AUTH401_3");

        verify(tokenStore, never()).deleteRefreshToken(any());
        verify(tokenStore, never()).findRefreshToken(any());
    }

    @Test
    @DisplayName("로그아웃하면 리프레시 토큰을 지우고 액세스 토큰을 차단 목록에 올린다")
    void 로그아웃() {
        // given
        String accessToken = jwtUtil.createAccessToken(USER_ID, Role.TEACHER);
        String jti = jwtUtil.parseAccessToken(accessToken).getId();

        // when
        authService.logout(JwtUtil.BEARER_PREFIX + accessToken);

        // then
        verify(tokenStore).deleteRefreshToken(USER_ID);
        verify(tokenStore).blacklistAccessToken(eq(jti), any(Duration.class));
    }

    @Test
    @DisplayName("리프레시 토큰으로 로그아웃을 시도하면 AUTH401_3 예외가 발생한다")
    void 리프레시_토큰으로는_로그아웃할_수_없다() {
        // given
        String refreshToken = jwtUtil.createRefreshToken(USER_ID, Role.TEACHER);

        // when & then
        assertThatThrownBy(() -> authService.logout(JwtUtil.BEARER_PREFIX + refreshToken))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode().getCode())
                .isEqualTo("AUTH401_3");

        verify(tokenStore, never()).blacklistAccessToken(anyString(), any(Duration.class));
    }
}
