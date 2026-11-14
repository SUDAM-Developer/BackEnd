package com.sudam.domain.settlement.service;

import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InvoiceAmountCalculator 회차가 모자랄 때 금액")
class InvoiceAmountCalculatorTest {

    @Test
    @DisplayName("인정 5회 × 회당 45,000원(360,000원 ÷ 8회) = 225,000원")
    void 인정_회차_곱하기_회당_단가() {
        // given
        TutoringPolicy policy = policy(360_000, 8);

        // when
        int amount = InvoiceAmountCalculator.shortfall(5, policy);

        // then
        assertThat(amount).isEqualTo(225_000);
    }

    @ParameterizedTest(name = "100,000원 ÷ 3회, 인정 {0}회 → {1}원")
    @CsvSource({"1, 33333", "2, 66666", "0, 0"})
    @DisplayName("회당 단가는 원 미만을 버린 뒤 곱한다 (100,000 ÷ 3 → 33,333)")
    void 회당_단가는_원_미만_버림(int recognized, int expected) {
        // given
        TutoringPolicy policy = policy(100_000, 3);

        // when
        int amount = InvoiceAmountCalculator.shortfall(recognized, policy);

        // then
        assertThat(amount).isEqualTo(expected);
    }

    private TutoringPolicy policy(int tuitionFee, int settlementSessionCnt) {
        TeacherProfile teacher = TeacherProfile.create(
                User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app"), "한서윤", List.of(Subject.ENGLISH), null);
        Tutoring tutoring = Tutoring.builder()
                .teacher(teacher)
                .subject(Subject.ENGLISH)
                .gradeLevel(9)
                .gradeSchoolYear(2026)
                .build();
        return TutoringPolicy.builder()
                .tutoring(tutoring)
                .effectiveCycleNo(1)
                .settlementSessionCnt(settlementSessionCnt)
                .paymentTiming(PaymentTiming.POSTPAID)
                .tuitionFee(tuitionFee)
                .bankCode("004")
                .bankName("국민")
                .accountNumber("12345601491492")
                .accountHolder("한서윤")
                .build();
    }
}
