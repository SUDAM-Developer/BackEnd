package com.sudam.global.security.oauth2.handler;

import com.sudam.global.code.BaseErrorCode;
import com.sudam.global.security.exception.AuthErrorCode;
import com.sudam.global.security.exception.SocialLoginException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final String failureRedirectUri;

    public OAuth2LoginFailureHandler(@Value("${app.oauth2.failure-redirect-uri}") String failureRedirectUri) {
        this.failureRedirectUri = failureRedirectUri;
    }

    /***
     * 함수 기능: 소셜 로그인 실패 원인을 로그로 남기고 프론트로 리다이렉트한다.
     * @param exception 플젝에서 정의한 SocialLoginException 또는 Spring Security가 던진 OAuth2AuthenticationException
     * @throws IOException 리다이렉트 응답을 쓰지 못한 경우
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {

        BaseErrorCode errorCode = (exception instanceof SocialLoginException ex)
                ? ex.getErrorCode()
                : AuthErrorCode.SOCIAL_LOGIN_FAILED;

        String oauth2ErrorCode = (exception instanceof OAuth2AuthenticationException ex)
                ? ex.getError().getErrorCode()
                : exception.getClass().getSimpleName();

        log.warn("[소셜 로그인 실패] code={}, oauth2Error={}, message={}",
                errorCode.getCode(), oauth2ErrorCode, exception.getMessage());

        // 실패 사유는 코드만 전달한다. 사용자에게 보여줄 문구는 프론트가 코드로 결정한다.
        response.sendRedirect(failureRedirectUri + "#code=" + errorCode.getCode());
    }
}
