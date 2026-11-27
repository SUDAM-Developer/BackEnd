package com.sudam.global.idempotency.exception;

import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.BaseErrorCode;

public class IdempotencyException extends ProjectException {

    public IdempotencyException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
