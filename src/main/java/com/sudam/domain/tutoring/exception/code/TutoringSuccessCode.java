package com.sudam.domain.tutoring.exception.code;

import com.sudam.global.exception.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TutoringSuccessCode implements BaseSuccessCode {

    CREATED(HttpStatus.CREATED, "TUTORING201_1", "과외가 만들어졌습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
