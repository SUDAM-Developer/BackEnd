package com.sudam.global.crypto;

import com.sudam.global.exception.ProjectException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("계좌번호 AES-GCM 변환기")
class AccountNumberConverterTest {

    // 테스트 전용 고정 키 (32바이트).
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("sudam-other-test-key-32-bytes!!!".getBytes());
    private static final String ACCOUNT_NUMBER = "12345601491492";

    private final AccountNumberConverter converter = new AccountNumberConverter(KEY);

    @Test
    @DisplayName("암호화한 값을 복호화하면 원래 계좌번호가 나온다")
    void 암호화_후_복호화하면_원문과_같다() {
        // when
        String encrypted = converter.convertToDatabaseColumn(ACCOUNT_NUMBER);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        // then
        assertThat(encrypted).isNotEqualTo(ACCOUNT_NUMBER).doesNotContain(ACCOUNT_NUMBER);
        assertThat(decrypted).isEqualTo(ACCOUNT_NUMBER);
    }

    @Test
    @DisplayName("같은 계좌번호도 암호화할 때마다 암호문이 다르다")
    void 같은_평문도_암호문이_매번_다르다() {
        // when
        String first = converter.convertToDatabaseColumn(ACCOUNT_NUMBER);
        String second = converter.convertToDatabaseColumn(ACCOUNT_NUMBER);

        // then
        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo(ACCOUNT_NUMBER);
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo(ACCOUNT_NUMBER);
    }

    @Test
    @DisplayName("14자리 계좌번호의 암호문은 컬럼 길이 255 안에 들어간다")
    void 암호문은_컬럼_길이_안에_들어간다() {
        // when
        String encrypted = converter.convertToDatabaseColumn(ACCOUNT_NUMBER);

        // then
        assertThat(encrypted).hasSizeLessThanOrEqualTo(255);
    }

    @Test
    @DisplayName("NULL은 그대로 NULL로 변환한다")
    void NULL은_그대로_둔다() {
        // when & then
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("다른 키로 만든 암호문을 복호화하면 COMMON500 예외가 발생한다")
    void 다른_키의_암호문은_복호화할_수_없다() {
        // given
        String encrypted = new AccountNumberConverter(OTHER_KEY).convertToDatabaseColumn(ACCOUNT_NUMBER);

        // when & then
        assertThatThrownBy(() -> converter.convertToEntityAttribute(encrypted))
                .isInstanceOf(ProjectException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo("COMMON500");
    }

    @Test
    @DisplayName("키가 32바이트가 아니면 생성 시점에 실패한다")
    void 키_길이가_틀리면_생성할_수_없다() {
        // given
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        // when & then
        assertThatThrownBy(() -> new AccountNumberConverter(shortKey))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키가 비어 있으면 생성 시점에 실패한다")
    void 키가_비어_있으면_생성할_수_없다() {
        // when & then
        assertThatThrownBy(() -> new AccountNumberConverter(""))
                .isInstanceOf(IllegalStateException.class);
    }
}
