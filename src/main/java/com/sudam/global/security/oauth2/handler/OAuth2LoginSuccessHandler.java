package com.sudam.global.security.oauth2.handler;

import com.sudam.global.security.oauth2.CustomOAuth2User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    /***
     * 함수 기능: 소셜 로그인 성공 시 인증된 회원 정보를 로그로 남긴다.
     * @param authentication principal에 CustomOAuth2User를 담고 있는 OAuth2AuthenticationToken
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) {

        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        String provider = ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();

        log.info("[소셜 로그인 성공] provider={}, userId={}, role={}",
                provider, principal.getUserId(), principal.getRole());

        // TODO: JWT를 발급한 뒤 프론트로 리다이렉트하여 토큰을 전달한다.
    }
}