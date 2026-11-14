package com.sudam.global.idempotency.exception.code;

import com.sudam.global.exception.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// 상태 코드는 IETF Idempotency-Key 헤더 초안(누락 400 · 동시 처리 409 · 키 재사용 422)을 따른다.
@Getter
@RequiredArgsConstructor
public enum IdempotencyErrorCode implements BaseErrorCode {

    KEY_INVALID(HttpStatus.BAD_REQUEST, "IDEMPOTENCY400_1", "멱등 키(Idempotency-Key) 헤더가 없거나 형식이 올바르지 않습니다."),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "IDEMPOTENCY409_1", "같은 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요."),
    KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY422_1", "이미 다른 요청에 사용한 멱등 키입니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
