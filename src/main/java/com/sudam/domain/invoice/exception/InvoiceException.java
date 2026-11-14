package com.sudam.domain.invoice.exception;

import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.BaseErrorCode;

public class InvoiceException extends ProjectException {

    public InvoiceException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
