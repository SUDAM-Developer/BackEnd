package com.sudam.global.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 붙인 컨트롤러 메서드는 Idempotency-Key 헤더(UUID)를 필수로 받고, 같은 키의 재요청에는 첫 성공 응답을 그대로 돌려준다.
 * 입금 · 결제처럼 두 번 실행되면 안 되는 POST에 붙인다. 처리는 {@link IdempotencyInterceptor}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
