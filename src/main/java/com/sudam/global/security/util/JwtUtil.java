package com.sudam.global.security.util;

import com.sudam.domain.user.enums.Role;
import com.sudam.domain.auth.exception.code.AuthErrorCode;
import com.sudam.domain.auth.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    public static final String BEARER_PREFIX = "Bearer ";

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
                   @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {

        // 서명 키는 요청마다 만들 필요가 없어 생성 시점에 한 번만 만든다.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /***
     * 함수 기능: 액세스 토큰을 발급한다. 인증에 필요한 최소 정보(userId, 권한)만 담는다.
     *          로그아웃·탈퇴 시 이 토큰 하나만 차단할 수 있도록 jti를 발급마다 새로 만든다.
     * @param userId 회원 식별자. 표준 클레임 sub에 담는다
     * @param role 회원 권한
     * @return 서명된 액세스 토큰
     */
    public String createAccessToken(Long userId, Role role) {

        Date now = new Date();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(key)
                .compact();
    }

    /***
     * 함수 기능: 리프레시 토큰을 발급한다.
     *          재발급할 액세스 토큰에 권한을 넣어야 하므로 권한도 함께 담는다 — 덕분에 재발급이 DB를 조회하지 않는다.
     *          jti는 토큰 값을 유일하게 만들기 위한 것이다. iat·exp가 초 단위로 직렬화되므로
     *          jti가 없으면 같은 초에 재발급한 토큰이 이전 토큰과 글자까지 같아져 재사용 감지가 뚫린다.
     * @param userId 회원 식별자
     * @param role 회원 권한
     * @return 서명된 리프레시 토큰
     */
    public String createRefreshToken(Long userId, Role role) {

        Date now = new Date();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(key)
                .compact();
    }

    /***
     * 함수 기능: 액세스 토큰임을 확인하고 클레임을 꺼낸다.
     * @param token Bearer 접두사를 제거한 토큰 문자열
     * @return 검증된 클레임
     * @throws AuthException 서명 불일치·만료·형식 오류이거나 액세스 토큰이 아닌 경우
     */
    public Claims parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    /***
     * 함수 기능: 리프레시 토큰임을 확인하고 클레임을 꺼낸다.
     *          액세스 토큰을 리프레시 토큰 자리에 보내면 여기서 막힌다 — 그대로 통과시키면
     *          저장된 리프레시 토큰과 값이 달라 탈취로 오판하고 정상 세션을 끊게 된다.
     * @param token 쿠키에서 꺼낸 토큰 문자열
     * @return 검증된 클레임
     * @throws AuthException 서명 불일치·만료·형식 오류이거나 리프레시 토큰이 아닌 경우
     */
    public Claims parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    /***
     * 함수 기능: 클레임에서 권한을 꺼낸다. 권한 클레임 이름을 이 클래스 밖으로 노출하지 않는다.
     * @param claims 검증된 클레임
     * @return 회원 권한
     * @throws AuthException role 클레임이 없거나 알 수 없는 값인 경우
     */
    public Role getRole(Claims claims) {

        String role = claims.get(CLAIM_ROLE, String.class);

        return Arrays.stream(Role.values())
                .filter(value -> value.name().equals(role))
                .findFirst()
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_TOKEN));
    }

    /***
     * 함수 기능: 토큰의 남은 유효 기간을 구한다. 차단 목록 TTL을 이 값으로 잡아 목록이 무한히 커지지 않게 한다.
     * @param claims 검증된 클레임
     * @return 남은 유효 기간. 이미 지났으면 0
     */
    public Duration getRemainingExpiration(Claims claims) {

        long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();

        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    // 서명·만료를 검증하고 기대한 종류의 토큰인지까지 확인한다.
    private Claims parse(String token, String expectedType) {

        Claims claims;

        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        // 기대한 토큰 타입과 다를 경우 예외
        if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        return claims;
    }
}
