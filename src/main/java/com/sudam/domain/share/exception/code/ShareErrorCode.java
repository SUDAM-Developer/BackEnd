package com.sudam.domain.share.exception.code;

import com.sudam.global.exception.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ShareErrorCode implements BaseErrorCode {

    UNSUPPORTED_KIND(HttpStatus.BAD_REQUEST, "SHARE400_1", "아직 지원하지 않는 공유 종류입니다."),
    SOURCE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "SHARE400_2", "공유할 대상(sourceId)을 함께 보내 주세요."),
    // 받는 사람이 없거나, 그 과외의 학부모가 아니거나, 삭제됐거나, 링크가 없을 때. 남의 과외 참여자도 '없음'으로 보인다.
    RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "SHARE404_1", "받는 사람을 찾을 수 없습니다."),
    RECIPIENT_REJECTED(HttpStatus.CONFLICT, "SHARE409_1", "수신을 거부한 받는 사람입니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
