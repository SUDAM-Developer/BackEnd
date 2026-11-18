package com.sudam.domain.share.exception;

import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.BaseErrorCode;

public class ShareException extends ProjectException {

    public ShareException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
