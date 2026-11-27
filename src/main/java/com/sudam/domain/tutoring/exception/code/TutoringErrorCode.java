package com.sudam.domain.tutoring.exception.code;

import com.sudam.global.exception.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TutoringErrorCode implements BaseErrorCode {

    // Notion 명세의 에러 이름을 저장소 코드 규칙(도메인 + 상태 + 순번)으로 옮겼다 (D9).
    SCHEDULE_DUPLICATE_DAY(HttpStatus.BAD_REQUEST, "TUTORING400_1", "같은 요일 일정이 두 번 들어갔습니다."),
    INVALID_LESSON_TIME(HttpStatus.BAD_REQUEST, "TUTORING400_2", "수업 시간을 확인해 주세요."),
    DUPLICATE_PHONE(HttpStatus.BAD_REQUEST, "TUTORING400_3", "같은 번호가 두 번 들어갔습니다."),
    UNSUPPORTED_FIELD(HttpStatus.BAD_REQUEST, "TUTORING400_4", "아직 지원하지 않는 항목입니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "TUTORING400_5", "입력값을 확인해 주세요."),
    TEACHER_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "TUTORING404_1", "선생님 프로필을 찾을 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
