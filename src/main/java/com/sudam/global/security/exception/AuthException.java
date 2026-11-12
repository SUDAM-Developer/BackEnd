package com.sudam.global.security.exception;

import com.sudam.global.code.BaseErrorCode;
import com.sudam.global.exception.ProjectException;

public class AuthException extends ProjectException {

    public AuthException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
