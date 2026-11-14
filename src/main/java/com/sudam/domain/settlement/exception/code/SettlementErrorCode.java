package com.sudam.domain.settlement.exception.code;

import com.sudam.global.exception.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements BaseErrorCode {

    // 과외 만들기가 사이클 1 정책을 항상 만들므로 생기면 데이터 문제다. 클라이언트가 고칠 수 없어 500으로 둔다.
    POLICY_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "SETTLEMENT500_1", "정산에 적용할 과외 정책을 찾을 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
