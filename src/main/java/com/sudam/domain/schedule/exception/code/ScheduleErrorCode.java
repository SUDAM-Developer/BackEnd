package com.sudam.domain.schedule.exception.code;

import com.sudam.global.exception.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode implements BaseErrorCode {

    // Notion 명세의 TUTORING404_1. 저장소 코드 규칙(도메인 + 상태 + 순번)을 따른다 (D9).
    TUTORING_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE404_1", "확인할 수 없는 과외입니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
