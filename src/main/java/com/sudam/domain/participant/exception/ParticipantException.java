package com.sudam.domain.participant.exception;

import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.BaseErrorCode;

public class ParticipantException extends ProjectException {

    public ParticipantException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
