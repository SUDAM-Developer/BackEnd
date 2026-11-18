package com.sudam.domain.share.exception.code;

import com.sudam.global.exception.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ShareSuccessCode implements BaseSuccessCode {

    SHARE_CREATED(HttpStatus.CREATED, "SHARE201_1", "공유 정보가 생성되었습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
