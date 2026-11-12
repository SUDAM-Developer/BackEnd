package com.sudam.domain.auth.exception.code;

import com.sudam.global.exception.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {

    TOKEN_REISSUED(HttpStatus.OK, "AUTH200_1", "토큰을 재발급했습니다."),
    LOGGED_OUT(HttpStatus.OK, "AUTH200_2", "로그아웃되었습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;

}
