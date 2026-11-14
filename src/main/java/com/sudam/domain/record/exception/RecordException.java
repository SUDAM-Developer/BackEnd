package com.sudam.domain.record.exception;

import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.BaseErrorCode;

public class RecordException extends ProjectException {

    public RecordException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
