package com.sudam.global.crypto;

import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.GeneralErrorCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 계좌번호를 AES-256-GCM으로 암호화해 저장하는 JPA 변환기 (ERD 공통 규칙).
 * 저장 형식은 Base64(IV 12바이트 + 암호문 + 인증 태그 16바이트)이고, 원문 14자리 기준 56자라 컬럼 길이 255에 들어간다.
 * 매번 새 IV를 쓰므로 같은 계좌번호도 암호문이 매번 달라 DB 값으로 같은 계좌인지 알 수 없다.
 * Hibernate가 스프링 빈 컨테이너로 생성하므로 생성자에서 설정값을 주입받는다.
 */
@Slf4j
@Component
@Converter
public class AccountNumberConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountNumberConverter(@Value("${crypto.account-encryption-key}") String base64Key) {
        this.key = new SecretKeySpec(decodeKey(base64Key), "AES");
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            log.error("[계좌번호 암호화 실패]", e);
            throw new ProjectException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(dbData));
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException | java.nio.BufferUnderflowException e) {
            // 키가 바뀌었거나 값이 변조되면 인증 태그 검증에서 실패한다.
            log.error("[계좌번호 복호화 실패]", e);
            throw new ProjectException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 키가 없거나 길이가 틀리면 첫 저장 때가 아니라 기동 시점에 바로 실패시킨다.
    private static byte[] decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("ACCOUNT_ENCRYPTION_KEY가 설정되지 않았습니다.");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ACCOUNT_ENCRYPTION_KEY가 Base64 형식이 아닙니다.", e);
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("ACCOUNT_ENCRYPTION_KEY는 Base64로 인코딩한 32바이트여야 합니다.");
        }
        return decoded;
    }
}
