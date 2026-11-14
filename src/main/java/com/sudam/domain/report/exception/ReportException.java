package com.sudam.domain.report.exception;

import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.BaseErrorCode;

public class ReportException extends ProjectException {

    public ReportException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
