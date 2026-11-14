package com.sudam.global.seed;

import com.sudam.domain.invoice.dto.response.InvoiceItemResponse;
import com.sudam.domain.invoice.dto.response.InvoiceListResponse;
import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.enums.InvoiceStatus;
import com.sudam.domain.invoice.enums.InvoiceStatusLineType;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoIncomeCardTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);
    private static final LocalDate CYCLE1_START = LocalDate.of(2026, 10, 10);

    private final List<Invoice> invoices = new ArrayList<>();
    private InvoiceListResponse response;

    @BeforeEach
    void setUp() {
        TeacherProfile teacher = TeacherProfile.create(
                User.createTeacher(Provider.KAKAO, "seed-teacher-1", "seed-teacher@sudam.test"),
                "한서윤", List.of(Subject.ENGLISH), null);
        List<InvoiceItemResponse> items = DemoIncomeCard.ALL.stream()
                .map(card -> {
                    Tutoring tutoring = Tutoring.builder()
                            .teacher(teacher).subject(Subject.ENGLISH).studentName(card.studentName())
                            .gradeLevel(card.gradeLevel()).gradeSchoolYear(2026).build();
                    TutoringPolicy policy = TutoringPolicy.builder()
                            .tutoring(tutoring).effectiveCycleNo(1)
                            .settlementSessionCnt(card.settlementSessionCount())
                            .paymentTiming(PaymentTiming.PREPAID).tuitionFee(card.tuitionFee())
                            .bankCode("004").bankName("국민은행").accountNumber("000000-00-000001")
                            .accountHolder("한서윤").build();
                    Participant parent = Participant.createParent(tutoring, ParticipantLabel.MOTHER, "01000000001");
                    Invoice invoice = card.issueInvoice(policy, 2L, CYCLE1_START, parent, NOW);
                    invoices.add(invoice);
                    return InvoiceItemResponse.of(invoice, 0, card.unpaidReminderEnabled());
                })
                .toList();
        response = InvoiceListResponse.from(items);
    }

    @Test
    @DisplayName("스냅샷은 정산 서비스의 선불 청구처럼 정산한 사이클 1 기준 인정 회차 · 시작일을 담는다")
    void 스냅샷은_정산한_사이클_기준() {
        // given: setUp

        // when & then
        assertThat(invoices).allSatisfy(invoice -> {
            assertThat(invoice.getSnapshot().recognizedCount()).isEqualTo(invoice.getSettlementSessionCnt());
            assertThat(invoice.getRecognizedCount()).isEqualTo(invoice.getSettlementSessionCnt());
            assertThat(invoice.getSnapshot().cycleStartDate()).isEqualTo(CYCLE1_START);
            assertThat(invoice.getSnapshot().canceledLessons()).isEmpty();
            assertThat(invoice.getBilledCycleId()).isEqualTo(2L);
            assertThat(invoice.getReportId()).isNull();
        });
    }

    @Test
    @DisplayName("수입 화면 카드 4장의 요약은 1,200,000 / 400,000 / 800,000이다")
    void 요약_금액() {
        // given: setUp에서 카드 4장을 청구로 만들어 목록 응답으로 바꿨다

        // when
        InvoiceListResponse.Summary summary = response.summary();

        // then
        assertThat(summary.totalAmount()).isEqualTo(1_200_000L);
        assertThat(summary.paidAmount()).isEqualTo(400_000L);
        assertThat(summary.unpaidAmount()).isEqualTo(800_000L);
        assertThat(response.confirmRequestedCount()).isEqualTo(1);
        assertThat(response.unpaidCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("카드는 박지후 → 윤서아 → 이도윤 → 김하린 순서이고 상태와 안내 줄이 화면과 맞다")
    void 카드_순서와_상태() {
        // given: setUp

        // when
        List<InvoiceItemResponse> items = response.items();

        // then
        assertThat(items).extracting(InvoiceItemResponse::studentName)
                .containsExactly("박지후", "윤서아", "이도윤", "김하린");
        assertThat(items).extracting(InvoiceItemResponse::status)
                .containsExactly(InvoiceStatus.CONFIRM_REQUESTED, InvoiceStatus.PENDING,
                        InvoiceStatus.PARTIAL, InvoiceStatus.PAID);
        assertThat(items).extracting(item -> item.statusLine().type())
                .containsExactly(InvoiceStatusLineType.PARENT_CONFIRMED, InvoiceStatusLineType.REMINDER_SCHEDULED,
                        InvoiceStatusLineType.PARTIAL_PAID, InvoiceStatusLineType.NONE);
        assertThat(items).extracting(InvoiceItemResponse::invoiceName)
                .containsExactly("다음 8회차", "다음 8회차", "다음 4회차", "다음 8회차");
        assertThat(items.get(0).statusLine().at()).isEqualTo(NOW.minusHours(2));
        assertThat(items.get(2).statusLine().at()).isEqualTo(NOW.minusDays(1));
    }
}
