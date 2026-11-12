package com.sudam.domain.auth.exception;

import com.sudam.global.exception.code.BaseErrorCode;
import com.sudam.global.exception.ProjectException;

public class AuthException extends ProjectException {

    public AuthException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
