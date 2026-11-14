package com.sudam.domain.invoice.exception.code;

import com.sudam.global.exception.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InvoiceSuccessCode implements BaseSuccessCode {

    INVOICES_FOUND(HttpStatus.OK, "INVOICE200_1", "입금 현황이 성공적으로 조회되었습니다."),
    PAYMENT_CONFIRMED(HttpStatus.CREATED, "INVOICE201_1", "입금이 성공적으로 확인되었습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
