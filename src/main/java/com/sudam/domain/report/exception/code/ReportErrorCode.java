package com.sudam.domain.report.exception.code;

import com.sudam.global.exception.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements BaseErrorCode {

    // 다른 선생님의 리포트도 같은 코드로 응답해 리포트 ID가 있는지조차 드러내지 않는다.
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT404_1", "정산 리포트를 찾을 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
