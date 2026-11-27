package com.sudam.global.idempotency;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 같은 멱등 키로 다른 요청이 왔는지 가려내기 위한 요청 지문.
 * 본문은 바이트 그대로 비교한다 (같은 클라이언트의 재시도는 같은 바이트를 보낸다).
 */
public final class IdempotencyFingerprint {

    private IdempotencyFingerprint() {
    }

    /***
     * 함수 기능: 메서드 · 경로 · 쿼리 · 본문으로 SHA-256 지문을 만든다.
     * @return 64자 소문자 hex
     */
    public static String of(String method, String requestUri, @Nullable String query, byte[] body) {
        String head = method + " " + requestUri + (query != null ? "?" + query : "") + "\n";
        MessageDigest digest = sha256();
        digest.update(head.getBytes(StandardCharsets.UTF_8));
        digest.update(body);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // 모든 JVM이 SHA-256을 갖추도록 규정돼 있어 여기 올 수 없다.
            throw new IllegalStateException(e);
        }
    }
}
