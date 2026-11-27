package com.sudam.global.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IdempotencyFingerprint 요청 지문")
class IdempotencyFingerprintTest {

    private static final String URI = "/api/v1/invoices/1/payments";
    private static final byte[] BODY = "{\"amount\":1000}".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("같은 요청은 같은 64자 hex 지문이다")
    void 같은_요청은_같은_지문() {
        // when
        String first = IdempotencyFingerprint.of("POST", URI, null, BODY);
        String second = IdempotencyFingerprint.of("POST", URI, null, BODY.clone());

        // then
        assertThat(first).isEqualTo(second).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("빈 본문도 지문을 계산한다")
    void 빈_본문() {
        assertThat(IdempotencyFingerprint.of("POST", URI, null, new byte[0])).hasSize(64);
    }

    static Stream<Arguments> differentRequests() {
        return Stream.of(
                Arguments.of("본문 다름", "POST", URI, null, "{\"amount\":1001}".getBytes(StandardCharsets.UTF_8)),
                Arguments.of("경로 다름", "POST", "/api/v1/invoices/2/payments", null, BODY),
                Arguments.of("메서드 다름", "PUT", URI, null, BODY),
                Arguments.of("쿼리 있음", "POST", URI, "a=1", BODY)
        );
    }

    @ParameterizedTest(name = "{0}이면 지문이 다르다")
    @MethodSource("differentRequests")
    @DisplayName("메서드 · 경로 · 쿼리 · 본문 중 하나라도 다르면 지문이 다르다")
    void 다른_요청은_다른_지문(String label, String method, String uri, String query, byte[] body) {
        // given
        String base = IdempotencyFingerprint.of("POST", URI, null, BODY);

        // when & then
        assertThat(IdempotencyFingerprint.of(method, uri, query, body)).isNotEqualTo(base);
    }
}
