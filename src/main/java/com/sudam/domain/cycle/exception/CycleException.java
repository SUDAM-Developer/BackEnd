package com.sudam.domain.cycle.exception;

import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.BaseErrorCode;

public class CycleException extends ProjectException {

    public CycleException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
