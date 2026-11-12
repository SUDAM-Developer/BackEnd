package com.sudam.domain.user.exception;

import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.BaseErrorCode;

public class UserException extends ProjectException {

    public UserException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
