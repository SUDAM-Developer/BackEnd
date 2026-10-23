package com.sudam.global.security.oauth2.handler;

import com.sudam.global.code.BaseErrorCode;
import com.sudam.global.security.exception.AuthErrorCode;
import com.sudam.global.security.exception.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    /***
     * 함수 기능: 소셜 로그인 실패 원인을 로그로 남긴다.
     * @param exception 플젝에서 정의한 AuthException 또는 Spring Security가 던진 OAuth2AuthenticationException
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) {

        BaseErrorCode errorCode = (exception instanceof AuthException ex)
                ? ex.getErrorCode()
                : AuthErrorCode.SOCIAL_LOGIN_FAILED;

        String oauth2ErrorCode = (exception instanceof OAuth2AuthenticationException ex)
                ? ex.getError().getErrorCode()
                : exception.getClass().getSimpleName();

        log.warn("[소셜 로그인 실패] code={}, oauth2Error={}, message={}",
                errorCode.getCode(), oauth2ErrorCode, exception.getMessage());

        // TODO: 프론트 에러 페이지로 리다이렉트하여 실패 원인을 전달한다.
    }
}