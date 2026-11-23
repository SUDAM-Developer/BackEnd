package com.sudam.domain.schedule.exception.code;

import com.sudam.global.exception.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScheduleSuccessCode implements BaseSuccessCode {

    MONTHLY_FOUND(HttpStatus.OK, "SCHEDULE200_1", "한 달 달력이 성공적으로 조회되었습니다."),
    DAILY_FOUND(HttpStatus.OK, "SCHEDULE200_2", "그날 일정이 성공적으로 조회되었습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
