package com.sudam.domain.participant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkTokenGeneratorTest {

    private final LinkTokenGenerator generator = new LinkTokenGenerator();

    @Test
    @DisplayName("토큰은 32바이트 URL 안전 Base64(패딩 없음) 43자다")
    void 토큰_형식() {
        // when
        String token = generator.generate();

        // then
        assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    @DisplayName("호출할 때마다 다른 토큰이 나온다")
    void 토큰_중복_없음() {
        // when
        String first = generator.generate();
        String second = generator.generate();

        // then
        assertThat(first).isNotEqualTo(second);
    }
}
