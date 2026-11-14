package com.sudam.domain.user.exception.code;

import com.sudam.global.exception.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserSuccessCode implements BaseSuccessCode {

    WITHDRAWN(HttpStatus.OK, "USER200_1", "회원 탈퇴가 완료되었습니다."),
    ME_FOUND(HttpStatus.OK, "USER200_2", "사용자 정보가 조회되었습니다."),
    SETTINGS_FOUND(HttpStatus.OK, "USER200_3", "설정 정보가 조회되었습니다."),
    PROFILE_UPDATED(HttpStatus.OK, "USER200_4", "프로필이 수정되었습니다."),
    TERMS_AGREED(HttpStatus.CREATED, "USER201_1", "약관 동의가 저장되었습니다."),
    PROFILE_CREATED(HttpStatus.CREATED, "USER201_2", "프로필이 등록되었습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;

}
