package com.sudam.global.security.exception;

import com.sudam.global.code.BaseErrorCode;
import lombok.Getter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

@Getter
public class AuthException extends OAuth2AuthenticationException {

    private final BaseErrorCode errorCode;

    public AuthException(BaseErrorCode errorCode) {
        super(new OAuth2Error(errorCode.getCode(), errorCode.getMessage(), null), errorCode.getMessage());
        this.errorCode = errorCode;
    }
}