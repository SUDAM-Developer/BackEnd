package com.sudam.domain.user.exception.code;

import com.sudam.global.exception.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserSuccessCode implements BaseSuccessCode {

    WITHDRAWN(HttpStatus.OK, "USER200_1", "회원 탈퇴가 완료되었습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;

}
