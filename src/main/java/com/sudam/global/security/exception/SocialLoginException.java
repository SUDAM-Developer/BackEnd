package com.sudam.global.security.exception;

import com.sudam.global.code.BaseErrorCode;
import lombok.Getter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/***
 * 소셜 로그인 흐름에서 던지는 예외.
 * OAuth2LoginAuthenticationFilter가 그 자리에서 낚아채 OAuth2LoginFailureHandler로 보낸다.
 */
@Getter
public class SocialLoginException extends OAuth2AuthenticationException {

    private final BaseErrorCode errorCode;

    public SocialLoginException(BaseErrorCode errorCode) {
        super(new OAuth2Error(errorCode.getCode(), errorCode.getMessage(), null), errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
