package com.sudam.global.security.oauth2.handler;

import com.sudam.global.security.oauth2.CustomOAuth2User;
import com.sudam.global.security.token.TokenStore;
import com.sudam.global.security.util.CookieUtil;
import com.sudam.global.security.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final TokenStore tokenStore;
    private final String successRedirectUri;

    public OAuth2LoginSuccessHandler(JwtUtil jwtUtil,
                                     CookieUtil cookieUtil,
                                     TokenStore tokenStore,
                                     @Value("${app.oauth2.success-redirect-uri}") String successRedirectUri) {
        this.jwtUtil = jwtUtil;
        this.cookieUtil = cookieUtil;
        this.tokenStore = tokenStore;
        this.successRedirectUri = successRedirectUri;
    }

    /***
     * 함수 기능: 소셜 로그인 성공 시 토큰을 발급해 프론트로 리다이렉트한다.
     * @param authentication principal에 CustomOAuth2User를 담고 있는 OAuth2AuthenticationToken
     * @throws IOException 리다이렉트 응답을 쓰지 못한 경우
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        String provider = ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();

        String accessToken = jwtUtil.createAccessToken(principal.getUserId(), principal.getRole());

        // 서버가 발급한 RT는 Redis에 저장하고 쿠키에 담아 응답한다.
        String refreshToken = jwtUtil.createRefreshToken(principal.getUserId(), principal.getRole());
        tokenStore.saveRefreshToken(principal.getUserId(), refreshToken);
        cookieUtil.addRefreshTokenCookie(response, refreshToken);

        log.info("[소셜 로그인 성공] provider={}, userId={}, role={}",
                provider, principal.getUserId(), principal.getRole());

        // AT는 쿼리 파라미터로 보내면 서버 로그·Referer에 남으므로 프래그먼트로 전달한다.
        response.sendRedirect(successRedirectUri + "#accessToken=" + accessToken);
    }
}
