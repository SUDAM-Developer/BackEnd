package com.sudam.domain.schedule.exception;

import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.BaseErrorCode;

public class ScheduleException extends ProjectException {

    public ScheduleException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
