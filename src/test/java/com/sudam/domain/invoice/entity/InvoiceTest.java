package com.sudam.domain.invoice.entity;

import com.sudam.domain.invoice.enums.AdjustedBy;
import com.sudam.domain.invoice.enums.InvoiceKind;
import com.sudam.domain.invoice.enums.InvoiceStatus;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.global.exception.ProjectException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Invoice 엔티티")
class InvoiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);
    private static final int AMOUNT = 400_000;

    @Nested
    @DisplayName("issue")
    class Issue {

        @Test
        @DisplayName("정책의 선불/후불 · 수업료 · 정산 회차 · 단가 · 계좌를 복사하고 입금 대기로 시작한다")
        void 정책을_복사하고_입금_대기로_시작한다() {
            // given
            TutoringPolicy policy = policy();
            InvoiceSnapshot snapshot = InvoiceSnapshot.of(policy, 8, null, null);

            // when
            Invoice invoice = Invoice.issue(policy, 10L, 20L, InvoiceKind.CURRENT, AMOUNT, snapshot);

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
            assertThat(invoice.getKind()).isEqualTo(InvoiceKind.CURRENT);
            assertThat(invoice.getBilledCycleId()).isEqualTo(10L);
            assertThat(invoice.getReportId()).isEqualTo(20L);
            assertThat(invoice.getPolicy()).isSameAs(policy);
            assertThat(invoice.getPaymentTiming()).isEqualTo(PaymentTiming.POSTPAID);
            assertThat(invoice.getTuitionFee()).isEqualTo(400_000);
            assertThat(invoice.getSettlementSessionCnt()).isEqualTo(8);
            assertThat(invoice.getUnitPrice()).isEqualTo(50_000);
            assertThat(invoice.getRecognizedCount()).isEqualTo(8);
            assertThat(invoice.getCalculatedAmount()).isEqualTo(AMOUNT);
            assertThat(invoice.getAmount()).isEqualTo(AMOUNT);
            assertThat(invoice.getSnapshot()).isEqualTo(snapshot);
            assertThat(invoice.getBankCode()).isEqualTo("004");
            assertThat(invoice.getBankName()).isEqualTo("국민");
            assertThat(invoice.getAccountNumber()).isEqualTo("12345601491492");
            assertThat(invoice.getAccountLast4()).isEqualTo("1492");
            assertThat(invoice.getAccountHolder()).isEqualTo("한서윤");
            assertThat(invoice.paidAmount()).isZero();
            assertThat(invoice.balance()).isEqualTo(AMOUNT);
        }
    }

    @Nested
    @DisplayName("recordPayment")
    class RecordPayment {

        @Test
        @DisplayName("금액을 비우면 잔액 전부를 반영하고 입금 완료가 된다")
        void 금액이_없으면_잔액_전부() {
            // given
            Invoice invoice = invoice();

            // when
            RecordedPayment result = invoice.recordPayment(null, NOW);

            // then
            assertThat(result.appliedAmount()).isEqualTo(AMOUNT);
            assertThat(result.payment().getAmount()).isEqualTo(AMOUNT);
            assertThat(result.payment().getConfirmedAt()).isEqualTo(NOW);
            assertThat(result.payment().getInvoice()).isSameAs(invoice);
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(invoice.getPayments()).containsExactly(result.payment());
        }

        @Test
        @DisplayName("잔액보다 큰 금액은 잔액까지만 반영한다")
        void 잔액보다_크면_잔액까지만() {
            // given
            Invoice invoice = invoice();
            invoice.recordPayment(100_000, NOW);

            // when
            RecordedPayment result = invoice.recordPayment(500_000, NOW.plusDays(1));

            // then
            assertThat(result.appliedAmount()).isEqualTo(300_000);
            assertThat(invoice.paidAmount()).isEqualTo(AMOUNT);
            assertThat(invoice.balance()).isZero();
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        }

        @Test
        @DisplayName("일부 입금이면 일부 입금 상태가 되고, 나머지를 받으면 입금 완료가 된다")
        void 일부_입금_후_완납() {
            // given
            Invoice invoice = invoice();

            // when
            invoice.recordPayment(150_000, NOW);

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIAL);
            assertThat(invoice.balance()).isEqualTo(250_000);

            // when
            invoice.recordPayment(250_000, NOW.plusDays(1));

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(invoice.getPayments()).hasSize(2);
        }

        @Test
        @DisplayName("입금 완료된 청구에 입금하면 INVOICE409_3 예외가 발생한다")
        void 완납_뒤_입금은_예외() {
            // given
            Invoice invoice = invoice();
            invoice.recordPayment(null, NOW);

            // when & then
            assertErrorCode(() -> invoice.recordPayment(10_000, NOW), "INVOICE409_3");
            assertThat(invoice.getPayments()).hasSize(1);
        }

        @Test
        @DisplayName("취소된 청구에 입금하면 INVOICE409_1 예외가 발생한다")
        void 취소된_청구에_입금하면_예외() {
            // given
            Invoice invoice = invoice();
            invoice.cancel(NOW);

            // when & then
            assertErrorCode(() -> invoice.recordPayment(10_000, NOW), "INVOICE409_1");
            assertThat(invoice.getPayments()).isEmpty();
        }

        @Test
        @DisplayName("확인 대기 중 일부 입금을 반영하면 일부 입금이 되고 확인 요청 기록은 남는다")
        void 확인_대기_중_입금하면_요청_기록은_남는다() {
            // given
            Invoice invoice = invoice();
            Participant parent = parent();
            invoice.requestConfirm(parent, NOW);

            // when
            invoice.recordPayment(100_000, NOW.plusHours(1));

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIAL);
            assertThat(invoice.getConfirmRequestedBy()).isSameAs(parent);
            assertThat(invoice.getConfirmRequestedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("cancelPayment")
    class CancelPayment {

        @Test
        @DisplayName("완납 청구의 입금을 되돌리면 합계에서 빠지고 입금 대기로 돌아간다")
        void 되돌리면_합계에서_빠진다() {
            // given
            Invoice invoice = invoice();
            InvoicePayment payment = invoice.recordPayment(null, NOW).payment();

            // when
            invoice.cancelPayment(payment, NOW.plusDays(1));

            // then
            assertThat(payment.isCanceled()).isTrue();
            assertThat(payment.getCanceledAt()).isEqualTo(NOW.plusDays(1));
            assertThat(invoice.paidAmount()).isZero();
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        }

        @Test
        @DisplayName("일부만 되돌리면 일부 입금 상태가 된다")
        void 일부만_되돌리면_일부_입금() {
            // given
            Invoice invoice = invoice();
            invoice.recordPayment(100_000, NOW);
            InvoicePayment second = invoice.recordPayment(null, NOW).payment();

            // when
            invoice.cancelPayment(second, NOW.plusDays(1));

            // then
            assertThat(invoice.paidAmount()).isEqualTo(100_000);
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIAL);
        }
    }

    @Nested
    @DisplayName("requestConfirm")
    class RequestConfirm {

        @Test
        @DisplayName("입금 대기에서 요청하면 확인 대기가 되고 요청한 학부모와 시각이 남는다")
        void 입금_대기에서_요청하면_확인_대기() {
            // given
            Invoice invoice = invoice();
            Participant parent = parent();

            // when
            invoice.requestConfirm(parent, NOW);

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CONFIRM_REQUESTED);
            assertThat(invoice.getConfirmRequestedBy()).isSameAs(parent);
            assertThat(invoice.getConfirmRequestedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("일부 입금에서도 요청할 수 있다")
        void 일부_입금에서도_요청할_수_있다() {
            // given
            Invoice invoice = invoice();
            invoice.recordPayment(100_000, NOW);

            // when
            invoice.requestConfirm(parent(), NOW);

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CONFIRM_REQUESTED);
        }

        @Test
        @DisplayName("이미 확인 대기면 두 번째 요청은 INVOICE409_5 예외가 발생하고 먼저 누른 학부모가 남는다")
        void 이미_확인_대기면_예외() {
            // given
            Invoice invoice = invoice();
            Participant first = parent();
            invoice.requestConfirm(first, NOW);

            // when & then
            assertErrorCode(() -> invoice.requestConfirm(parent(), NOW.plusMinutes(1)), "INVOICE409_5");
            assertThat(invoice.getConfirmRequestedBy()).isSameAs(first);
        }

        @Test
        @DisplayName("입금 완료면 INVOICE409_5 예외가 발생한다")
        void 입금_완료면_예외() {
            // given
            Invoice invoice = invoice();
            invoice.recordPayment(null, NOW);

            // when & then
            assertErrorCode(() -> invoice.requestConfirm(parent(), NOW), "INVOICE409_5");
        }

        @Test
        @DisplayName("취소된 청구면 INVOICE409_5 예외가 발생한다")
        void 취소된_청구면_예외() {
            // given
            Invoice invoice = invoice();
            invoice.cancel(NOW);

            // when & then
            assertErrorCode(() -> invoice.requestConfirm(parent(), NOW), "INVOICE409_5");
        }
    }

    @Nested
    @DisplayName("rejectConfirm")
    class RejectConfirm {

        @Test
        @DisplayName("입금이 없으면 입금 대기로 돌아가고 확인 요청 기록이 비워진다")
        void 입금이_없으면_입금_대기() {
            // given
            Invoice invoice = invoice();
            invoice.requestConfirm(parent(), NOW);

            // when
            invoice.rejectConfirm();

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
            assertThat(invoice.getConfirmRequestedBy()).isNull();
            assertThat(invoice.getConfirmRequestedAt()).isNull();
        }

        @Test
        @DisplayName("일부 입금이 있으면 일부 입금으로 돌아간다")
        void 일부_입금이_있으면_일부_입금() {
            // given
            Invoice invoice = invoice();
            invoice.recordPayment(100_000, NOW);
            invoice.requestConfirm(parent(), NOW);

            // when
            invoice.rejectConfirm();

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIAL);
            assertThat(invoice.getConfirmRequestedBy()).isNull();
        }

        @Test
        @DisplayName("확인 대기가 아니면 아무것도 바꾸지 않는다")
        void 확인_대기가_아니면_그대로() {
            // given
            Invoice invoice = invoice();
            invoice.recordPayment(100_000, NOW);

            // when
            invoice.rejectConfirm();

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIAL);
        }

        @Test
        @DisplayName("취소된 청구면 INVOICE409_1 예외가 발생한다")
        void 취소된_청구면_예외() {
            // given
            Invoice invoice = invoice();
            invoice.cancel(NOW);

            // when & then
            assertErrorCode(invoice::rejectConfirm, "INVOICE409_1");
        }
    }

    @Nested
    @DisplayName("adjustAmount")
    class AdjustAmount {

        @Test
        @DisplayName("금액을 바꾸면 이력이 남고 자동 계산 금액은 그대로다")
        void 금액을_바꾸면_이력이_남는다() {
            // given
            Invoice invoice = invoice();

            // when
            Optional<InvoiceAmountAdjustment> result = invoice.adjustAmount(350_000, "교재비 제외", AdjustedBy.TEACHER);

            // then
            assertThat(result).isPresent();
            InvoiceAmountAdjustment adjustment = result.get();
            assertThat(adjustment.getBeforeAmount()).isEqualTo(AMOUNT);
            assertThat(adjustment.getAfterAmount()).isEqualTo(350_000);
            assertThat(adjustment.getReason()).isEqualTo("교재비 제외");
            assertThat(adjustment.getAdjustedBy()).isEqualTo(AdjustedBy.TEACHER);
            assertThat(adjustment.getInvoice()).isSameAs(invoice);
            assertThat(invoice.getAmount()).isEqualTo(350_000);
            assertThat(invoice.getCalculatedAmount()).isEqualTo(AMOUNT);
            assertThat(invoice.getAdjustments()).containsExactly(adjustment);
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        }

        @Test
        @DisplayName("같은 금액이면 이력을 만들지 않는다")
        void 같은_금액이면_아무것도_안_한다() {
            // given
            Invoice invoice = invoice();

            // when
            Optional<InvoiceAmountAdjustment> result = invoice.adjustAmount(AMOUNT, "변경 없음", AdjustedBy.TEACHER);

            // then
            assertThat(result).isEmpty();
            assertThat(invoice.getAdjustments()).isEmpty();
        }

        @Test
        @DisplayName("입금 합계보다 적게 바꾸면 INVOICE409_2 예외가 발생한다")
        void 입금_합계보다_적으면_예외() {
            // given
            Invoice invoice = invoice();
            invoice.recordPayment(200_000, NOW);

            // when & then
            assertErrorCode(() -> invoice.adjustAmount(199_999, "감액", AdjustedBy.TEACHER), "INVOICE409_2");
            assertThat(invoice.getAmount()).isEqualTo(AMOUNT);
            assertThat(invoice.getAdjustments()).isEmpty();
        }

        @Test
        @DisplayName("취소된 청구면 INVOICE409_1 예외가 발생한다")
        void 취소된_청구면_예외() {
            // given
            Invoice invoice = invoice();
            invoice.cancel(NOW);

            // when & then
            assertErrorCode(() -> invoice.adjustAmount(300_000, "감액", AdjustedBy.TEACHER), "INVOICE409_1");
        }

        @Test
        @DisplayName("확인 대기 중 금액을 바꿔도 확인 대기 상태와 요청 기록이 유지된다")
        void 확인_대기면_상태_유지() {
            // given
            Invoice invoice = invoice();
            Participant parent = parent();
            invoice.recordPayment(100_000, NOW);
            invoice.requestConfirm(parent, NOW);

            // when
            invoice.adjustAmount(300_000, "감액", AdjustedBy.TEACHER);

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CONFIRM_REQUESTED);
            assertThat(invoice.getConfirmRequestedBy()).isSameAs(parent);
        }

        @Test
        @DisplayName("일부 입금 중 받은 금액만큼으로 줄이면 입금 완료가 된다")
        void 받은_만큼으로_줄이면_입금_완료() {
            // given
            Invoice invoice = invoice();
            invoice.recordPayment(150_000, NOW);

            // when
            invoice.adjustAmount(150_000, "진행 회차만큼", AdjustedBy.SYSTEM);

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(invoice.balance()).isZero();
        }

        @Test
        @DisplayName("입금 완료 뒤 금액을 늘리면 일부 입금이 된다")
        void 완납_뒤_늘리면_일부_입금() {
            // given
            Invoice invoice = invoice();
            invoice.recordPayment(null, NOW);

            // when
            invoice.adjustAmount(450_000, "보강 1회 추가", AdjustedBy.TEACHER);

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIAL);
            assertThat(invoice.balance()).isEqualTo(50_000);
        }
    }

    @Nested
    @DisplayName("cancel")
    class Cancel {

        @Test
        @DisplayName("취소하면 취소 상태와 취소 시각이 남는다")
        void 취소하면_상태와_시각이_남는다() {
            // given
            Invoice invoice = invoice();

            // when
            invoice.cancel(NOW);

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CANCELED);
            assertThat(invoice.getCanceledAt()).isEqualTo(NOW);
        }
    }

    private static TutoringPolicy policy() {
        return TutoringPolicy.builder()
                .effectiveCycleNo(1)
                .settlementSessionCnt(8)
                .paymentTiming(PaymentTiming.POSTPAID)
                .tuitionFee(400_000)
                .bankCode("004")
                .bankName("국민")
                .accountNumber("123456-01-491492")
                .accountHolder("한서윤")
                .build();
    }

    private static Invoice invoice() {
        TutoringPolicy policy = policy();
        return Invoice.issue(policy, 1L, null, InvoiceKind.CURRENT, AMOUNT, InvoiceSnapshot.of(policy, 8, null, null));
    }

    private static Participant parent() {
        return Participant.createParent(null, ParticipantLabel.MOTHER, "01012345678");
    }

    private static void assertErrorCode(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOf(ProjectException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo(code);
    }
}
