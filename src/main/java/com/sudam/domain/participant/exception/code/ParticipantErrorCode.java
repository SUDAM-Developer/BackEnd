package com.sudam.domain.participant.exception.code;

import com.sudam.global.exception.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ParticipantErrorCode implements BaseErrorCode {

    // 없는 토큰 · 폐기 · 만료 · 삭제를 구분하지 않는다. 링크를 가진 사람에게 어느 경우인지 알려 줄 필요가 없다.
    LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "PARTICIPANT404_1", "링크가 만료되었거나 올바르지 않습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
