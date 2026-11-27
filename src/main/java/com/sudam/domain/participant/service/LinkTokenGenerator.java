package com.sudam.domain.participant.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 학부모 링크 토큰 생성기. ERD 공통 규칙: SecureRandom 32바이트를 URL 안전 문자열(패딩 없음, 43자)로.
 */
@Component
public class LinkTokenGenerator {

    private static final int LINK_TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[LINK_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
