package com.sudam.domain.tutoring.entity;

import com.sudam.domain.tutoring.enums.PaymentTiming;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TutoringPolicy 생성")
class TutoringPolicyTest {

    @Test
    @DisplayName("하이픈이 섞인 계좌번호는 숫자만 저장하고 뒤 4자리를 따로 둔다")
    void 계좌번호는_숫자만_저장하고_뒤_4자리를_둔다() {
        // when
        TutoringPolicy policy = policyWithAccount("123456-01-491492");

        // then
        assertThat(policy.getAccountNumber()).isEqualTo("12345601491492");
        assertThat(policy.getAccountLast4()).isEqualTo("1492");
    }

    @Test
    @DisplayName("학생 당일 취소 인정 여부는 P0에서 항상 false로 생성된다")
    void 당일_취소_인정은_항상_false() {
        // when
        TutoringPolicy policy = policyWithAccount("1234567890");

        // then
        assertThat(policy.isSameDayCancelRecognized()).isFalse();
    }

    @Test
    @DisplayName("은행 목록에 없는 은행은 코드 없이 입력한 이름만 저장한다")
    void 목록에_없는_은행은_코드가_NULL() {
        // when
        TutoringPolicy policy = TutoringPolicy.builder()
                .effectiveCycleNo(1)
                .settlementSessionCnt(4)
                .paymentTiming(PaymentTiming.PREPAID)
                .tuitionFee(200_000)
                .bankCode(null)
                .bankName("증권사 CMA")
                .accountNumber("1234567890")
                .accountHolder("한서윤")
                .build();

        // then
        assertThat(policy.getBankCode()).isNull();
        assertThat(policy.getBankName()).isEqualTo("증권사 CMA");
    }

    private static TutoringPolicy policyWithAccount(String accountNumber) {
        return TutoringPolicy.builder()
                .effectiveCycleNo(1)
                .settlementSessionCnt(8)
                .paymentTiming(PaymentTiming.POSTPAID)
                .tuitionFee(360_000)
                .bankCode("004")
                .bankName("국민")
                .accountNumber(accountNumber)
                .accountHolder("한서윤")
                .build();
    }
}
