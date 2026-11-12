package com.sudam.global.exception;

import com.sudam.global.exception.code.BaseErrorCode;
import lombok.Getter;

@Getter
public class ProjectException extends RuntimeException {
    private final BaseErrorCode errorCode;

    public ProjectException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
