package com.sudam.domain.cycle.exception.code;

import com.sudam.global.exception.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CycleErrorCode implements BaseErrorCode {

    ALREADY_SETTLED(HttpStatus.CONFLICT, "CYCLE409_1", "이미 정산된 사이클입니다."),
    IN_PROGRESS_EXISTS(HttpStatus.CONFLICT, "CYCLE409_2", "이미 진행 중인 사이클이 있습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
