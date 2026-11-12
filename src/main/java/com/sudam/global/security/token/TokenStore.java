package com.sudam.global.security.token;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class TokenStore {

    // RT 키 접두사 (key=rt:{userId} val={refreshToken})
    private static final String REFRESH_KEY_PREFIX = "rt:";
    // AT 블랙리스트 키 접두사 (key=bl:{jti} val="logout")
    private static final String BLACKLIST_KEY_PREFIX = "bl:";

    // 차단 목록은 키의 존재 여부만 쓰므로 값은 "logout"으로 채운다.
    private static final String BLACKLIST_VALUE = "logout";

    private final StringRedisTemplate redisTemplate;
    private final Duration refreshTokenTtl;

    public TokenStore(StringRedisTemplate redisTemplate,
                      @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenTtl = Duration.ofMillis(refreshTokenExpiration);
    }

    /***
     * 함수 기능: 회원의 리프레시 토큰을 저장한다. 회원당 1개이므로 재발급 시 같은 키를 덮어쓰고 유효 기간도 다시 잡는다.
     * @param userId 회원 식별자
     * @param refreshToken 발급된 리프레시 토큰
     */
    public void saveRefreshToken(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(refreshKey(userId), refreshToken, refreshTokenTtl);
    }

    /***
     * 함수 기능: 저장된 리프레시 토큰을 조회한다.
     * @param userId 회원 식별자
     * @return 저장된 토큰. 로그아웃·탈퇴했거나 유효 기간이 지났으면 빈 Optional
     */
    public Optional<String> findRefreshToken(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(refreshKey(userId)));
    }

    /***
     * 함수 기능: 저장된 리프레시 토큰을 지운다. 로그아웃·탈퇴, 그리고 탈취가 의심될 때 호출한다.
     * @param userId 회원 식별자
     */
    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(refreshKey(userId));
    }

    /***
     * 함수 기능: 액세스 토큰을 차단 목록에 올린다. 유효 기간을 토큰의 남은 기간으로 잡아 스스로 사라지게 한다.
     * @param jti 액세스 토큰의 jti 클레임
     * @param ttl 토큰의 남은 유효 기간
     */
    public void blacklistAccessToken(String jti, Duration ttl) {

        // 이미 만료된 토큰은 서명 검증에서 걸러지므로 올려 둘 필요가 없다.
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }

        redisTemplate.opsForValue().set(blacklistKey(jti), BLACKLIST_VALUE, ttl);
    }

    /***
     * 함수 기능: 액세스 토큰이 차단 목록에 있는지 확인한다.
     * @param jti 액세스 토큰의 jti 클레임
     * @return 차단된 토큰이면 true
     */
    public boolean isBlacklisted(String jti) {
        return jti != null && redisTemplate.hasKey(blacklistKey(jti));
    }

    private String refreshKey(Long userId) {
        return REFRESH_KEY_PREFIX + userId;
    }

    private String blacklistKey(String jti) {
        return BLACKLIST_KEY_PREFIX + jti;
    }
}
