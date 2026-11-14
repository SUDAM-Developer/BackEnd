package com.sudam.domain.invoice.dto.response;

import com.sudam.domain.invoice.entity.CanceledLesson;
import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.entity.InvoiceSnapshot;
import com.sudam.domain.invoice.enums.InvoiceKind;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportInvoiceResponse 교육비 안내 변환")
class ReportInvoiceResponseTest {

    private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);

    @Test
    @DisplayName("선불 8회 · 단가 45,000 · 360,000원 청구는 차감 0이고 사이클 시작일은 스냅샷 시작일이다")
    void 선불_청구() {
        // given
        TutoringPolicy policy = policy(PaymentTiming.PREPAID, 360_000, 8);
        Invoice invoice = issue(policy, InvoiceKind.NEXT, 360_000,
                InvoiceSnapshot.of(policy, 8, SEP_14, List.of()));

        // when
        ReportInvoiceResponse response = ReportInvoiceResponse.of(invoice);

        // then
        assertThat(response.invoiceId()).isEqualTo(301L);
        assertThat(response.paymentTiming()).isEqualTo(PaymentTiming.PREPAID);
        assertThat(response.settlementSessionCnt()).isEqualTo(8);
        assertThat(response.billedCycleStartDate()).isEqualTo(SEP_14);
        assertThat(response.unitPrice()).isEqualTo(45_000);
        assertThat(response.canceledCount()).isZero();
        assertThat(response.deductionAmount()).isZero();
        assertThat(response.calculatedAmount()).isEqualTo(360_000);
        assertThat(response.amount()).isEqualTo(360_000);
    }

    @Test
    @DisplayName("후불 회차 부족(인정 5 · 정산 8 · 단가 45,000 · 자동 계산 225,000)이면 차감은 135,000이다")
    void 후불_회차_부족() {
        // given
        TutoringPolicy policy = policy(PaymentTiming.POSTPAID, 360_000, 8);
        Invoice invoice = issue(policy, InvoiceKind.FINAL, 225_000,
                InvoiceSnapshot.of(policy, 5, SEP_14, List.of()));

        // when
        ReportInvoiceResponse response = ReportInvoiceResponse.of(invoice);

        // then
        assertThat(response.deductionAmount()).isEqualTo(135_000);
        assertThat(response.calculatedAmount()).isEqualTo(225_000);
    }

    @Test
    @DisplayName("정산 없이 만든 선불 첫 청구(FIRST)는 사이클 시작일이 null이고 휴강 수는 0이다")
    void 선불_첫_청구() {
        // given
        TutoringPolicy policy = policy(PaymentTiming.PREPAID, 360_000, 8);
        Invoice invoice = issue(policy, InvoiceKind.FIRST, 360_000,
                InvoiceSnapshot.of(policy, null, null, null));

        // when
        ReportInvoiceResponse response = ReportInvoiceResponse.of(invoice);

        // then
        assertThat(response.billedCycleStartDate()).isNull();
        assertThat(response.canceledCount()).isZero();
    }

    @Test
    @DisplayName("스냅샷 휴강 내역이 2건이면 휴강 수는 2다")
    void 휴강_2건() {
        // given
        TutoringPolicy policy = policy(PaymentTiming.POSTPAID, 360_000, 8);
        List<CanceledLesson> canceled = List.of(
                new CanceledLesson(LocalDate.of(2026, 9, 16), "TEACHER"),
                new CanceledLesson(LocalDate.of(2026, 9, 23), "HOLIDAY"));
        Invoice invoice = issue(policy, InvoiceKind.CURRENT, 360_000,
                InvoiceSnapshot.of(policy, 8, SEP_14, canceled));

        // when
        ReportInvoiceResponse response = ReportInvoiceResponse.of(invoice);

        // then
        assertThat(response.canceledCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("단가를 원 미만 버림해 정산 회차 × 단가가 자동 계산 금액보다 작아도 차감은 0이다 (음수 아님)")
    void 단가_버림() {
        // given: 수업료 100,000 · 3회 → 단가 33,333, 3 × 33,333 = 99,999 < 100,000
        TutoringPolicy policy = policy(PaymentTiming.POSTPAID, 100_000, 3);
        Invoice invoice = issue(policy, InvoiceKind.CURRENT, 100_000,
                InvoiceSnapshot.of(policy, 3, SEP_14, List.of()));

        // when
        ReportInvoiceResponse response = ReportInvoiceResponse.of(invoice);

        // then
        assertThat(response.unitPrice()).isEqualTo(33_333);
        assertThat(response.deductionAmount()).isZero();
    }

    private static TutoringPolicy policy(PaymentTiming timing, int tuitionFee, int settlementSessionCnt) {
        return TutoringPolicy.builder()
                .effectiveCycleNo(1)
                .settlementSessionCnt(settlementSessionCnt)
                .paymentTiming(timing)
                .tuitionFee(tuitionFee)
                .bankCode("004")
                .bankName("국민")
                .accountNumber("123456-01-491492")
                .accountHolder("한서윤")
                .build();
    }

    // 저장 시점에 정해지는 ID는 setter가 없어 테스트에서 직접 넣는다.
    private static Invoice issue(TutoringPolicy policy, InvoiceKind kind, int amount, InvoiceSnapshot snapshot) {
        Invoice invoice = Invoice.issue(policy, 1L, 21L, kind, amount, snapshot);
        ReflectionTestUtils.setField(invoice, "id", 301L);
        return invoice;
    }
}
