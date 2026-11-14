package com.sudam.domain.record.exception.code;

import com.sudam.global.exception.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RecordErrorCode implements BaseErrorCode {

    SESSION_DATE_NOT_IN_SCHEDULE(HttpStatus.BAD_REQUEST, "RECORD400_1", "수업 일정에 없는 날짜입니다."),
    FUTURE_SESSION_DATE(HttpStatus.BAD_REQUEST, "RECORD400_2", "아직 하지 않은 수업은 기록할 수 없습니다."),
    INVALID_PROGRESS_RANGE(HttpStatus.BAD_REQUEST, "RECORD400_3", "끝 진도는 시작 진도보다 앞설 수 없습니다."),
    TUTORING_NOT_FOUND(HttpStatus.NOT_FOUND, "RECORD404_1", "과외를 찾을 수 없습니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "RECORD404_2", "수업 일정을 찾을 수 없습니다."),
    TEXTBOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "RECORD404_3", "교재를 찾을 수 없습니다."),
    ALREADY_RECORDED(HttpStatus.CONFLICT, "RECORD409_1", "이미 기록한 수업입니다."),
    NO_IN_PROGRESS_CYCLE(HttpStatus.CONFLICT, "RECORD409_2", "진행 중인 수업 사이클이 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
