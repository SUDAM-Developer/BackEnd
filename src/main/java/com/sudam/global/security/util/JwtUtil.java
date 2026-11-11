package com.sudam.global.security.util;

import com.sudam.domain.user.enums.Role;
import com.sudam.global.security.exception.AuthErrorCode;
import com.sudam.global.security.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String CLAIM_ROLE = "role";

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
     * @param userId 회원 식별자. 표준 클레임 sub에 담는다
     * @param role 회원 권한
     * @return 서명된 액세스 토큰
     */
    public String createAccessToken(Long userId, Role role) {

        Date now = new Date();

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(key)
                .compact();
    }

    /***
     * 함수 기능: 리프레시 토큰을 발급한다. 재발급에만 쓰이므로 권한을 담지 않는다.
     * @param userId 회원 식별자
     * @return 서명된 리프레시 토큰
     */
    public String createRefreshToken(Long userId) {

        Date now = new Date();

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(key)
                .compact();
    }

    /***
     * 함수 기능: 토큰의 서명과 만료를 검증하고 클레임을 꺼낸다.
     * @param token Bearer 접두사를 제거한 토큰 문자열
     * @return 검증된 클레임
     * @throws AuthException 서명 불일치·만료·형식 오류인 경우
     */
    public Claims parse(String token) {

        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    /***
     * 함수 기능: 클레임에서 권한을 꺼낸다. 권한 클레임 이름을 이 클래스 밖으로 노출하지 않는다.
     *          리프레시 토큰처럼 role이 없는 토큰이 들어오면 유효하지 않은 토큰으로 처리한다.
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
}
