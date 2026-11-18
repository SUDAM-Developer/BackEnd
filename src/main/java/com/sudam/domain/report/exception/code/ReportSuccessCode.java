package com.sudam.domain.report.exception.code;

import com.sudam.global.exception.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportSuccessCode implements BaseSuccessCode {

    REPORT_FOUND(HttpStatus.OK, "REPORT200_1", "정산 리포트를 조회했습니다."),
    COMMENT_FOUND(HttpStatus.OK, "REPORT200_2", "정산 리포트 코멘트를 조회했습니다."),
    COMMENT_SAVED(HttpStatus.OK, "REPORT200_3", "정산 리포트 코멘트를 저장하고 확인했습니다."),
    REPORT_SENT(HttpStatus.OK, "REPORT200_4", "정산 리포트를 보냈습니다."),
    DELIVERIES_FOUND(HttpStatus.OK, "REPORT200_5", "정산 리포트 목록을 조회했습니다."),
    PARENT_REPORT_FOUND(HttpStatus.OK, "REPORT200_6", "정산 리포트를 열었습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
