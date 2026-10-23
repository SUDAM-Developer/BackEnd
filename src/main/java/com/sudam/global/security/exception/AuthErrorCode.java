package com.sudam.global.security.exception;

import com.sudam.global.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH400_1", "지원하지 않는 소셜 로그인입니다."),
    REQUIRED_CONSENT_MISSING(HttpStatus.UNAUTHORIZED, "AUTH401_1", "필수 동의 항목에 동의해야 로그인할 수 있습니다."),
    SOCIAL_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH401_2", "소셜 로그인에 실패했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;

}