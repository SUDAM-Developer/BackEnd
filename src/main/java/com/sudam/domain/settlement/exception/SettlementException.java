package com.sudam.domain.settlement.exception;

import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.BaseErrorCode;

public class SettlementException extends ProjectException {

    public SettlementException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
