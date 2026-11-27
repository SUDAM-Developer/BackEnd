package com.sudam.domain.tutoring.exception;

import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.BaseErrorCode;

public class TutoringException extends ProjectException {

    public TutoringException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
