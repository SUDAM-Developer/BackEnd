package com.sudam.domain.user.exception.code;

import com.sudam.global.exception.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements BaseErrorCode {

    REQUIRED_TERMS_MISSING(HttpStatus.BAD_REQUEST, "USER400_1", "필수 약관에 모두 동의해야 합니다."),
    SUBJECT_OTHER_REQUIRED(HttpStatus.BAD_REQUEST, "USER400_2", "기타 과목명을 입력해 주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404_1", "해당 회원을 찾을 수 없습니다."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404_2", "프로필이 등록되지 않았습니다."),
    PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER409_1", "이미 등록된 프로필입니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;

}
