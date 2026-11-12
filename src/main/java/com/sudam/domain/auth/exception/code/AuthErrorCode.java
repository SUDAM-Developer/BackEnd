package com.sudam.domain.auth.exception.code;

import com.sudam.global.exception.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH400_1", "지원하지 않는 소셜 로그인입니다."),
    REQUIRED_CONSENT_MISSING(HttpStatus.UNAUTHORIZED, "AUTH401_1", "필수 동의 항목에 동의해야 로그인할 수 있습니다."),
    SOCIAL_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH401_2", "소셜 로그인에 실패했습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_3", "유효하지 않은 토큰입니다."),
    BLACKLISTED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_4", "로그아웃된 토큰입니다. 다시 로그인해 주세요."),
    // 탈취 의심 여부를 응답으로 알리지 않도록 아래 둘은 메시지를 같게 두고 코드로만 구분한다.
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH401_5", "다시 로그인해 주세요."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH401_6", "다시 로그인해 주세요."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;

}
