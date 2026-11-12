package com.sudam.domain.auth.service;

import com.sudam.domain.auth.dto.TokenPair;
import com.sudam.domain.auth.exception.code.AuthErrorCode;
import com.sudam.domain.auth.exception.AuthException;
import com.sudam.domain.user.enums.Role;
import com.sudam.global.security.token.TokenStore;
import com.sudam.global.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;

    /***
     * 함수 기능: 리프레시 토큰으로 액세스·리프레시 토큰을 함께 재발급한다(RTR).
     *          저장된 토큰을 새 값으로 덮어써 이전 토큰을 즉시 무효화한다.
     * @param refreshToken 쿠키에서 꺼낸 리프레시 토큰. 쿠키가 없으면 null
     * @return 새로 발급한 토큰 묶음
     * @throws AuthException 토큰이 유효하지 않거나(AUTH401_3), 저장된 토큰이 없거나(AUTH401_5),
     *                       저장된 토큰과 값이 다른 경우(AUTH401_6)
     */
    public TokenPair reissue(String refreshToken) {

        // null·빈 문자열은 파서가 IllegalArgumentException으로 거부하고 parse가 AUTH401_3으로 바꾼다.
        Claims claims = jwtUtil.parseRefreshToken(refreshToken);

        Long userId = Long.parseLong(claims.getSubject());
        Role role = jwtUtil.getRole(claims);

        String stored = tokenStore.findRefreshToken(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));

        // 이미 교체된 토큰이 다시 들어오면 탈취로 보고 그 회원의 세션을 모두 끊는다.
        if (!stored.equals(refreshToken)) {
            tokenStore.deleteRefreshToken(userId);
            log.warn("[리프레시 토큰 불일치] userId={}", userId);
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        String newRefreshToken = jwtUtil.createRefreshToken(userId, role);
        tokenStore.saveRefreshToken(userId, newRefreshToken);

        return new TokenPair(jwtUtil.createAccessToken(userId, role), newRefreshToken);
    }

    /***
     * 함수 기능: 리프레시 토큰을 지우고 지금 쓰인 액세스 토큰을 차단 목록에 올린다.
     * @param bearerHeader Authorization 헤더 값. 인증이 필요한 경로이므로 필터를 통과한 형식임이 보장된다
     */
    public void logout(String bearerHeader) {

        Claims claims = jwtUtil.parseAccessToken(bearerHeader.substring(JwtUtil.BEARER_PREFIX.length()));
        Long userId = Long.parseLong(claims.getSubject());

        tokenStore.deleteRefreshToken(userId);
        tokenStore.blacklistAccessToken(claims.getId(), jwtUtil.getRemainingExpiration(claims));

        log.info("[로그아웃] userId={}", userId);
    }
}
