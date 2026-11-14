package com.sudam.domain.report.exception.code;

import com.sudam.global.exception.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportSuccessCode implements BaseSuccessCode {

    REPORT_FOUND(HttpStatus.OK, "REPORT200_1", "정산 리포트를 조회했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
