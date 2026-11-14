package com.sudam.domain.invoice.exception.code;

import com.sudam.global.exception.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InvoiceErrorCode implements BaseErrorCode {

    INVOICE_NOT_FOUND(HttpStatus.NOT_FOUND, "INVOICE404_1", "교육비 안내를 찾을 수 없습니다."),
    INVOICE_CANCELED(HttpStatus.CONFLICT, "INVOICE409_1", "취소된 교육비 안내입니다."),
    AMOUNT_BELOW_PAID(HttpStatus.CONFLICT, "INVOICE409_2", "이미 받은 금액보다 적게 바꿀 수 없습니다."),
    ALREADY_PAID(HttpStatus.CONFLICT, "INVOICE409_3", "이미 입금 완료된 교육비입니다."),
    REPORT_ALREADY_SENT(HttpStatus.CONFLICT, "INVOICE409_4", "이미 보낸 정산 리포트의 교육비는 바꿀 수 없습니다."),
    CONFIRM_NOT_REQUESTABLE(HttpStatus.CONFLICT, "INVOICE409_5", "입금 확인을 요청할 수 없는 상태입니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
