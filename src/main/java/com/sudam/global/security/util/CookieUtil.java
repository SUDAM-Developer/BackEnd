package com.sudam.global.security.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieUtil {

    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final long refreshTokenExpiration;
    private final boolean secure;

    public CookieUtil(@Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
                      @Value("${app.cookie.secure}") boolean secure) {
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.secure = secure;
    }

    /***
     * 함수 기능: 리프레시 토큰을 HttpOnly 쿠키로 응답에 싣는다.
     *          JS에서 읽지 못하게 막고, 만료는 토큰 자체의 유효 기간과 맞춘다.
     *          Secure 여부는 프로파일별 app.cookie.secure를 따른다 (HTTPS인 개발 서버는 true, HTTP인 로컬은 false).
     *          프론트(sudam.app)와 API(api.sudam.app)는 같은 사이트라 SameSite=Lax로도 쿠키가 전달된다.
     * @param response 쿠키를 실을 응답
     * @param refreshToken 발급된 리프레시 토큰
     */
    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        addCookie(response, refreshToken, Duration.ofMillis(refreshTokenExpiration));
    }

    /***
     * 함수 기능: 리프레시 토큰 쿠키를 즉시 만료시킨다. 로그아웃·탈퇴 후 브라우저에 쿠키가 남지 않게 한다.
     *          속성(path·secure·sameSite 등)이 발급 시점과 같아야 브라우저가 같은 쿠키로 인식해 덮어쓴다.
     * @param response 쿠키를 실을 응답
     */
    public void deleteRefreshTokenCookie(HttpServletResponse response) {
        addCookie(response, "", Duration.ZERO);
    }

    private void addCookie(HttpServletResponse response, String value, Duration maxAge) {

        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
