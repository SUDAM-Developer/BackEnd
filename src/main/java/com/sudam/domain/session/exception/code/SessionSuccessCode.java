package com.sudam.domain.session.exception.code;

import com.sudam.global.exception.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SessionSuccessCode implements BaseSuccessCode {

    TODAY_FOUND(HttpStatus.OK, "SESSION200_1", "오늘 수업 목록이 조회되었습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
