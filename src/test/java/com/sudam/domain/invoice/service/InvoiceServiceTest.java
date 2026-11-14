package com.sudam.domain.invoice.service;

import com.sudam.domain.invoice.dto.request.ConfirmPaymentRequest;
import com.sudam.domain.invoice.dto.response.InvoiceItemResponse;
import com.sudam.domain.invoice.dto.response.InvoiceListResponse;
import com.sudam.domain.invoice.dto.response.PaymentConfirmResponse;
import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.entity.InvoiceSnapshot;
import com.sudam.domain.invoice.enums.InvoiceKind;
import com.sudam.domain.invoice.enums.InvoiceStatus;
import com.sudam.domain.invoice.enums.InvoiceStatusLineType;
import com.sudam.domain.invoice.repository.InvoiceRepository;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.entity.TutoringSetting;
import com.sudam.domain.tutoring.enums.ParentUpdateFrequency;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.tutoring.repository.TutoringSettingRepository;
import com.sudam.global.exception.ProjectException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceService 입금 현황 · 입금 확인")
class InvoiceServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);
    private static final Long TEACHER_ID = 1L;
    private static final Long OTHER_TEACHER_ID = 2L;
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final LocalDateTime NOV_10 = LocalDateTime.of(2026, 11, 10, 9, 0);
    private static final LocalDateTime OCT_10 = LocalDateTime.of(2026, 10, 10, 9, 0);

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private TutoringSettingRepository tutoringSettingRepository;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        invoiceService = new InvoiceService(invoiceRepository, tutoringSettingRepository, clock);
    }

    @Nested
    @DisplayName("statusLine")
    class StatusLine {

        @Test
        @DisplayName("확인 대기면 다시 보냄 · 안내 전보다 먼저 PARENT_CONFIRMED이고 시각은 요청 시각이다")
        void 확인_대기() {
            // given
            Invoice invoice = invoice(10L, tutoring(100L, TEACHER_ID), 360_000, NOV_10);
            set(invoice, "renotifiedAt", NOV_10.plusDays(1));
            invoice.requestConfirm(parent(), NOV_10.plusDays(2));
            stubList(List.of(invoice), List.of(invoice), reminderOn(100L));

            // when
            InvoiceItemResponse item = single();

            // then
            assertThat(item.statusLine().type()).isEqualTo(InvoiceStatusLineType.PARENT_CONFIRMED);
            assertThat(item.statusLine().at()).isEqualTo(NOV_10.plusDays(2));
        }

        @Test
        @DisplayName("일부 입금이면 다시 보냄보다 먼저 PARTIAL_PAID이고 시각은 마지막 입금 확인 시각이다")
        void 일부_입금() {
            // given
            Invoice invoice = invoice(10L, tutoring(100L, TEACHER_ID), 360_000, NOV_10);
            set(invoice, "notifiedAt", NOV_10);
            set(invoice, "renotifiedAt", NOV_10.plusDays(1));
            invoice.recordPayment(100_000, NOV_10.plusDays(2));
            invoice.recordPayment(20_000, NOV_10.plusDays(3));
            stubList(List.of(invoice), List.of(invoice), reminderOn(100L));

            // when
            InvoiceItemResponse item = single();

            // then
            assertThat(item.statusLine().type()).isEqualTo(InvoiceStatusLineType.PARTIAL_PAID);
            assertThat(item.statusLine().at()).isEqualTo(NOV_10.plusDays(3));
        }

        @Test
        @DisplayName("다시 보낸 적이 있으면 미입금 알림 예정보다 먼저 RESENT이고 시각은 다시 보낸 시각이다")
        void 다시_보냄() {
            // given
            Invoice invoice = invoice(10L, tutoring(100L, TEACHER_ID), 360_000, NOV_10);
            set(invoice, "notifiedAt", NOV_10);
            set(invoice, "renotifiedAt", NOV_10.plusDays(1));
            stubList(List.of(invoice), List.of(invoice), reminderOn(100L));

            // when
            InvoiceItemResponse item = single();

            // then
            assertThat(item.statusLine().type()).isEqualTo(InvoiceStatusLineType.RESENT);
            assertThat(item.statusLine().at()).isEqualTo(NOV_10.plusDays(1));
        }

        @Test
        @DisplayName("미입금 알림을 켰고 아직 보내기 전인 입금 대기면 안내 전이어도 REMINDER_SCHEDULED다")
        void 미입금_알림_예정() {
            // given
            Invoice invoice = invoice(10L, tutoring(100L, TEACHER_ID), 360_000, NOV_10);
            stubList(List.of(invoice), List.of(invoice), reminderOn(100L));

            // when
            InvoiceItemResponse item = single();

            // then
            assertThat(item.statusLine().type()).isEqualTo(InvoiceStatusLineType.REMINDER_SCHEDULED);
            assertThat(item.statusLine().at()).isNull();
        }

        @Test
        @DisplayName("미입금 알림이 꺼져 있고 안내를 보내지 않았으면 NOT_SENT다")
        void 안내_전() {
            // given
            Invoice invoice = invoice(10L, tutoring(100L, TEACHER_ID), 360_000, NOV_10);
            stubList(List.of(invoice), List.of(invoice), List.of());

            // when
            InvoiceItemResponse item = single();

            // then
            assertThat(item.statusLine().type()).isEqualTo(InvoiceStatusLineType.NOT_SENT);
        }

        @Test
        @DisplayName("안내를 보냈고 미입금 알림도 이미 보냈으면 NONE이다")
        void 그_외() {
            // given
            Invoice invoice = invoice(10L, tutoring(100L, TEACHER_ID), 360_000, NOV_10);
            set(invoice, "notifiedAt", NOV_10);
            set(invoice, "reminderSentAt", NOV_10.plusDays(1));
            stubList(List.of(invoice), List.of(invoice), reminderOn(100L));

            // when
            InvoiceItemResponse item = single();

            // then
            assertThat(item.statusLine().type()).isEqualTo(InvoiceStatusLineType.NONE);
        }

        @Test
        @DisplayName("입금 완료면 다시 보냈거나 안내 전이어도 NONE이다")
        void 완납() {
            // given
            Invoice invoice = invoice(10L, tutoring(100L, TEACHER_ID), 360_000, NOV_10);
            set(invoice, "renotifiedAt", NOV_10.plusDays(1));
            invoice.recordPayment(null, NOV_10.plusDays(2));
            stubList(List.of(invoice), List.of(), reminderOn(100L));

            // when
            InvoiceItemResponse item = single();

            // then
            assertThat(item.status()).isEqualTo(InvoiceStatus.PAID);
            assertThat(item.statusLine().type()).isEqualTo(InvoiceStatusLineType.NONE);
            assertThat(item.statusLine().at()).isNull();
        }
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "FIRST, 첫 8회차",
            "NEXT, 다음 8회차",
            "CURRENT, 이번 8회차",
            "FINAL, 마지막 정산"
    })
    @DisplayName("invoiceName은 청구 종류와 스냅샷 정산 회차로 만든다")
    void invoiceName(InvoiceKind kind, String expected) {
        // given
        Invoice invoice = invoice(10L, tutoring(100L, TEACHER_ID), 360_000, NOV_10, kind);
        stubList(List.of(invoice), List.of(invoice), List.of());

        // when
        InvoiceItemResponse item = single();

        // then
        assertThat(item.invoiceName()).isEqualTo(expected);
    }

    @Test
    @DisplayName("previousUnpaidAmount는 같은 과외에서 먼저 만든 청구들의 잔액 합이다")
    void 앞선_청구_잔액_합() {
        // given
        Tutoring tutoring = tutoring(100L, TEACHER_ID);
        Invoice first = invoice(1L, tutoring, 300_000, OCT_10);
        first.recordPayment(100_000, OCT_10.plusDays(1)); // 잔액 200,000
        Invoice second = invoice(2L, tutoring, 360_000, NOV_10);
        Invoice otherTutoring = invoice(3L, tutoring(200L, TEACHER_ID), 50_000, OCT_10);
        stubList(List.of(second), List.of(first, second, otherTutoring), List.of());

        // when
        InvoiceListResponse response = invoiceService.getInvoices(TEACHER_ID, NOVEMBER);

        // then
        assertThat(itemOf(response, 2L).previousUnpaidAmount()).isEqualTo(200_000);
        assertThat(itemOf(response, 1L).previousUnpaidAmount()).isZero();
        assertThat(itemOf(response, 3L).previousUnpaidAmount()).isZero();
    }

    @Nested
    @DisplayName("대상 달 필터")
    class MonthFilter {

        @Test
        @DisplayName("그 달 안내분과 이전 달 잔액 남은 청구를 담고, 다음 달 안내분은 담지 않는다")
        void 이전_달_잔액_포함() {
            // given
            Tutoring tutoring = tutoring(100L, TEACHER_ID);
            Invoice octoberUnpaid = invoice(1L, tutoring, 300_000, OCT_10);
            Invoice novemberPaid = invoice(2L, tutoring, 360_000, NOV_10);
            novemberPaid.recordPayment(null, NOV_10);
            Invoice decemberUnpaid = invoice(3L, tutoring, 360_000, OCT_10);
            set(decemberUnpaid, "notifiedAt", LocalDateTime.of(2026, 12, 1, 9, 0));
            stubList(List.of(novemberPaid), List.of(octoberUnpaid, decemberUnpaid), List.of());

            // when
            InvoiceListResponse response = invoiceService.getInvoices(TEACHER_ID, NOVEMBER);

            // then
            assertThat(response.items()).extracting(InvoiceItemResponse::invoiceId).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("이전 달 청구는 완납 · 취소를 빼고 잔액이 남을 수 있는 상태만 조회한다")
        @SuppressWarnings("unchecked")
        void 이전_달_완납_제외() {
            // given
            stubList(List.of(), List.of(), List.of());
            ArgumentCaptor<Collection<InvoiceStatus>> statuses = ArgumentCaptor.forClass(Collection.class);

            // when
            invoiceService.getInvoices(TEACHER_ID, NOVEMBER);

            // then
            verify(invoiceRepository).findAllOfTeacherByStatusIn(eq(TEACHER_ID), statuses.capture());
            assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                    InvoiceStatus.PENDING, InvoiceStatus.PARTIAL, InvoiceStatus.CONFIRM_REQUESTED);
        }

        @Test
        @DisplayName("month가 없으면 한국 시간 이번 달 [1일 0시, 다음 달 1일 0시)로 조회한다")
        void 기본은_이번_달() {
            // given
            stubList(List.of(), List.of(), List.of());

            // when
            invoiceService.getInvoices(TEACHER_ID, null);

            // then
            verify(invoiceRepository).findAllOfTeacherAnnouncedBetween(TEACHER_ID,
                    LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 12, 1, 0, 0));
        }
    }

    @Test
    @DisplayName("확인 대기 → 입금 대기 → 일부 입금 → 입금 완료 순이고 같은 상태는 최신순이다")
    void 정렬() {
        // given
        Tutoring tutoring = tutoring(100L, TEACHER_ID);
        Invoice paid = invoice(1L, tutoring, 100_000, NOV_10.plusDays(5));
        paid.recordPayment(null, NOV_10);
        Invoice partial = invoice(2L, tutoring, 100_000, NOV_10.plusDays(4));
        partial.recordPayment(10_000, NOV_10);
        Invoice olderPending = invoice(3L, tutoring, 100_000, NOV_10);
        Invoice newerPending = invoice(4L, tutoring, 100_000, NOV_10.plusDays(3));
        Invoice confirmRequested = invoice(5L, tutoring, 100_000, NOV_10.minusDays(5));
        confirmRequested.requestConfirm(parent(), NOV_10);
        stubList(List.of(paid, partial, olderPending, newerPending, confirmRequested), List.of(), List.of());

        // when
        InvoiceListResponse response = invoiceService.getInvoices(TEACHER_ID, NOVEMBER);

        // then
        assertThat(response.items()).extracting(InvoiceItemResponse::invoiceId).containsExactly(5L, 4L, 3L, 2L, 1L);
    }

    @Test
    @DisplayName("summary와 건수는 items에서 더한 값과 같다")
    void 요약은_items_합() {
        // given
        Tutoring tutoring = tutoring(100L, TEACHER_ID);
        Invoice partial = invoice(1L, tutoring, 360_000, NOV_10);
        partial.recordPayment(120_000, NOV_10);
        Invoice paid = invoice(2L, tutoring, 200_000, NOV_10);
        paid.recordPayment(null, NOV_10);
        Invoice confirmRequested = invoice(3L, tutoring(200L, TEACHER_ID), 150_000, NOV_10);
        confirmRequested.requestConfirm(parent(), NOV_10);
        Invoice octoberPending = invoice(4L, tutoring, 80_000, OCT_10);
        stubList(List.of(partial, paid, confirmRequested), List.of(octoberPending, partial, confirmRequested), List.of());

        // when
        InvoiceListResponse response = invoiceService.getInvoices(TEACHER_ID, NOVEMBER);

        // then
        List<InvoiceItemResponse> items = response.items();
        assertThat(items).hasSize(4);
        assertThat(response.summary().totalAmount())
                .isEqualTo(items.stream().mapToLong(InvoiceItemResponse::amount).sum())
                .isEqualTo(790_000);
        assertThat(response.summary().paidAmount())
                .isEqualTo(items.stream().mapToLong(InvoiceItemResponse::paidAmount).sum())
                .isEqualTo(320_000);
        assertThat(response.summary().unpaidAmount())
                .isEqualTo(items.stream().mapToLong(InvoiceItemResponse::balance).sum())
                .isEqualTo(470_000);
        assertThat(response.confirmRequestedCount()).isEqualTo(1);
        assertThat(response.unpaidCount()).isEqualTo(3);
    }

    @Nested
    @DisplayName("confirmPayment")
    class ConfirmPayment {

        @Test
        @DisplayName("다른 선생님의 청구면 INVOICE404_1이고 입금을 반영하지 않는다")
        void 다른_선생님_청구() {
            // given
            Invoice invoice = invoice(10L, tutoring(100L, OTHER_TEACHER_ID), 360_000, NOV_10);
            given(invoiceRepository.findByIdForUpdate(10L)).willReturn(Optional.of(invoice));

            // when & then
            assertErrorCode(() -> invoiceService.confirmPayment(TEACHER_ID, 10L, request(120_000)), "INVOICE404_1");
            assertThat(invoice.getPayments()).isEmpty();
            verify(invoiceRepository, never()).flush();
        }

        @Test
        @DisplayName("없는 청구면 INVOICE404_1이다")
        void 없는_청구() {
            // given
            given(invoiceRepository.findByIdForUpdate(anyLong())).willReturn(Optional.empty());

            // when & then
            assertErrorCode(() -> invoiceService.confirmPayment(TEACHER_ID, 99L, request(120_000)), "INVOICE404_1");
        }

        @Test
        @DisplayName("360,000원 청구에 120,000 입금하면 PARTIAL · PARTIAL_PAID · 잔액 240,000, 다시 300,000이면 240,000만 반영돼 PAID다")
        void 일부_입금_후_초과_입금() {
            // given
            Invoice invoice = invoice(10L, tutoring(100L, TEACHER_ID), 360_000, NOV_10);
            stubConfirm(invoice);

            // when
            PaymentConfirmResponse first = invoiceService.confirmPayment(TEACHER_ID, 10L,
                    new ConfirmPaymentRequest(120_000, LocalDateTime.of(2026, 11, 15, 20, 0)));

            // then
            assertThat(first.appliedAmount()).isEqualTo(120_000);
            assertThat(first.item().status()).isEqualTo(InvoiceStatus.PARTIAL);
            assertThat(first.item().statusLine().type()).isEqualTo(InvoiceStatusLineType.PARTIAL_PAID);
            assertThat(first.item().statusLine().at()).isEqualTo(LocalDateTime.of(2026, 11, 15, 20, 0));
            assertThat(first.item().paidAmount()).isEqualTo(120_000);
            assertThat(first.item().balance()).isEqualTo(240_000);

            // when
            PaymentConfirmResponse second = invoiceService.confirmPayment(TEACHER_ID, 10L, request(300_000));

            // then
            assertThat(second.appliedAmount()).isEqualTo(240_000);
            assertThat(second.item().status()).isEqualTo(InvoiceStatus.PAID);
            assertThat(second.item().balance()).isZero();
            assertThat(second.item().statusLine().type()).isEqualTo(InvoiceStatusLineType.NONE);
        }

        @Test
        @DisplayName("금액 · 시각을 비우면 잔액 전부를 지금 시각으로 반영한다")
        void 본문_없이_전액() {
            // given
            Invoice invoice = invoice(10L, tutoring(100L, TEACHER_ID), 360_000, NOV_10);
            stubConfirm(invoice);

            // when
            PaymentConfirmResponse response =
                    invoiceService.confirmPayment(TEACHER_ID, 10L, ConfirmPaymentRequest.empty());

            // then
            assertThat(response.appliedAmount()).isEqualTo(360_000);
            assertThat(invoice.getPayments().getFirst().getConfirmedAt()).isEqualTo(NOW);
            assertThat(response.item().status()).isEqualTo(InvoiceStatus.PAID);
        }

        @Test
        @DisplayName("이미 완납한 청구에 입금 확인하면 INVOICE409_3이다")
        void 완납_후_입금() {
            // given
            Invoice invoice = invoice(10L, tutoring(100L, TEACHER_ID), 360_000, NOV_10);
            invoice.recordPayment(null, NOV_10);
            given(invoiceRepository.findByIdForUpdate(10L)).willReturn(Optional.of(invoice));

            // when & then
            assertErrorCode(() -> invoiceService.confirmPayment(TEACHER_ID, 10L, request(1_000)), "INVOICE409_3");
        }

        @Test
        @DisplayName("응답 item은 같은 과외의 앞선 청구 잔액을 previousUnpaidAmount로 담는다")
        void 응답_item_앞선_청구_잔액() {
            // given
            Tutoring tutoring = tutoring(100L, TEACHER_ID);
            Invoice earlier = invoice(5L, tutoring, 80_000, OCT_10);
            Invoice invoice = invoice(10L, tutoring, 360_000, NOV_10);
            given(invoiceRepository.findByIdForUpdate(10L)).willReturn(Optional.of(invoice));
            given(invoiceRepository.findAllByTutoringIdAndStatusIn(eq(100L), anyCollection()))
                    .willReturn(List.of(earlier, invoice));
            given(tutoringSettingRepository.findAllById(anyIterable())).willReturn(List.of());

            // when
            PaymentConfirmResponse response = invoiceService.confirmPayment(TEACHER_ID, 10L, request(100_000));

            // then
            assertThat(response.item().previousUnpaidAmount()).isEqualTo(80_000);
        }
    }

    private void stubList(List<Invoice> announced, List<Invoice> unsettled, List<TutoringSetting> settings) {
        given(invoiceRepository.findAllOfTeacherAnnouncedBetween(eq(TEACHER_ID), any(), any())).willReturn(announced);
        given(invoiceRepository.findAllOfTeacherByStatusIn(eq(TEACHER_ID), anyCollection())).willReturn(unsettled);
        given(tutoringSettingRepository.findAllById(anyIterable())).willReturn(settings);
    }

    private void stubConfirm(Invoice invoice) {
        given(invoiceRepository.findByIdForUpdate(invoice.getId())).willReturn(Optional.of(invoice));
        given(invoiceRepository.findAllByTutoringIdAndStatusIn(eq(invoice.getTutoring().getId()), anyCollection()))
                .willReturn(List.of(invoice));
        given(tutoringSettingRepository.findAllById(anyIterable())).willReturn(List.of());
    }

    private InvoiceItemResponse single() {
        List<InvoiceItemResponse> items = invoiceService.getInvoices(TEACHER_ID, NOVEMBER).items();
        assertThat(items).hasSize(1);
        return items.getFirst();
    }

    private static InvoiceItemResponse itemOf(InvoiceListResponse response, Long invoiceId) {
        return response.items().stream()
                .filter(item -> item.invoiceId().equals(invoiceId))
                .findFirst()
                .orElseThrow();
    }

    private static Tutoring tutoring(Long id, Long teacherId) {
        Tutoring tutoring = Tutoring.builder()
                .teacherId(teacherId)
                .studentName("서아")
                .gradeLevel(10)
                .gradeSchoolYear(2026)
                .build();
        set(tutoring, "id", id);
        return tutoring;
    }

    private static Invoice invoice(Long id, Tutoring tutoring, int amount, LocalDateTime createdAt) {
        return invoice(id, tutoring, amount, createdAt, InvoiceKind.CURRENT);
    }

    private static Invoice invoice(Long id, Tutoring tutoring, int amount, LocalDateTime createdAt, InvoiceKind kind) {
        TutoringPolicy policy = TutoringPolicy.builder()
                .tutoring(tutoring)
                .effectiveCycleNo(1)
                .settlementSessionCnt(8)
                .paymentTiming(PaymentTiming.POSTPAID)
                .tuitionFee(400_000)
                .bankCode("004")
                .bankName("국민")
                .accountNumber("123456-01-491492")
                .accountHolder("한서윤")
                .build();
        Invoice invoice = Invoice.issue(policy, 1L, null, kind, amount, InvoiceSnapshot.of(policy, 8, null, null));
        set(invoice, "id", id);
        set(invoice, "createdAt", createdAt);
        return invoice;
    }

    private static List<TutoringSetting> reminderOn(Long tutoringId) {
        TutoringSetting setting = TutoringSetting.builder()
                .parentUpdateFrequency(ParentUpdateFrequency.AT_SETTLEMENT)
                .unpaidReminderEnabled(true)
                .build();
        set(setting, "tutoringId", tutoringId);
        return List.of(setting);
    }

    private static Participant parent() {
        return Participant.createParent(null, ParticipantLabel.MOTHER, "01012345678");
    }

    private static ConfirmPaymentRequest request(int amount) {
        return new ConfirmPaymentRequest(amount, NOW);
    }

    // 청구 · 과외 엔티티에 setter가 없어 저장 시점에 정해지는 값(ID · 생성 시각 · 웹훅 시각)은 테스트에서 직접 넣는다.
    private static void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }

    private static void assertErrorCode(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOf(ProjectException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo(code);
    }
}
