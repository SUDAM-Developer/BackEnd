package com.sudam.domain.record.exception.code;

import com.sudam.global.exception.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RecordSuccessCode implements BaseSuccessCode {

    RECORD_SAVED(HttpStatus.CREATED, "RECORD201_1", "수업 기록을 저장했습니다."),
    RECORD_FORM_FOUND(HttpStatus.OK, "RECORD200_1", "기록 화면 정보를 조회했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
