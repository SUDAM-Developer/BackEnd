package com.sudam.domain.invoice.service;

import com.sudam.domain.invoice.dto.request.AdjustInvoiceAmountRequest;
import com.sudam.domain.invoice.dto.response.ReportInvoiceResponse;
import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.entity.InvoiceAmountAdjustment;
import com.sudam.domain.invoice.entity.InvoiceSnapshot;
import com.sudam.domain.invoice.enums.AdjustedBy;
import com.sudam.domain.invoice.enums.InvoiceKind;
import com.sudam.domain.invoice.enums.InvoiceStatus;
import com.sudam.domain.invoice.repository.InvoiceRepository;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.enums.ReportStatus;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import com.sudam.global.exception.ProjectException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportInvoiceService 교육비 안내 조회 · 교육비 수정")
class ReportInvoiceServiceTest {

    private static final Long TEACHER_ID = 1L;
    private static final Long OTHER_TEACHER_ID = 2L;
    private static final Long REPORT_ID = 21L;
    private static final Long INVOICE_ID = 301L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private SettlementReportRepository settlementReportRepository;

    @InjectMocks
    private ReportInvoiceService reportInvoiceService;

    @Nested
    @DisplayName("교육비 안내 조회")
    class GetReportInvoice {

        @Test
        @DisplayName("리포트에 붙은 내 청구를 교육비 안내로 돌려준다")
        void 조회_성공() {
            // given
            Invoice invoice = invoice(tutoring(TEACHER_ID), 360_000);
            given(invoiceRepository.findActiveByReportIdAndTeacherId(REPORT_ID, TEACHER_ID))
                    .willReturn(Optional.of(invoice));

            // when
            ReportInvoiceResponse response = reportInvoiceService.getReportInvoice(TEACHER_ID, REPORT_ID);

            // then
            assertThat(response.invoiceId()).isEqualTo(INVOICE_ID);
            assertThat(response.amount()).isEqualTo(360_000);
        }

        @Test
        @DisplayName("남의 리포트거나 청구가 없는 리포트면(조회 결과 없음) INVOICE404_1이다")
        void 남의_리포트_또는_청구_없음() {
            // given
            given(invoiceRepository.findActiveByReportIdAndTeacherId(REPORT_ID, TEACHER_ID))
                    .willReturn(Optional.empty());

            // when & then
            assertErrorCode(() -> reportInvoiceService.getReportInvoice(TEACHER_ID, REPORT_ID), "INVOICE404_1");
        }
    }

    @Nested
    @DisplayName("교육비 수정")
    class AdjustAmount {

        @Test
        @DisplayName("360,000원을 375,000원으로 바꾸면 금액만 바뀌고 자동 계산 금액은 그대로이며 이력 1행이 생긴다")
        void 수정_성공() {
            // given
            Invoice invoice = invoice(tutoring(TEACHER_ID), 360_000);
            stubLockedInvoice(invoice);
            stubReport(ReportStatus.GENERATED);

            // when
            ReportInvoiceResponse response = reportInvoiceService.adjustAmount(TEACHER_ID, INVOICE_ID,
                    new AdjustInvoiceAmountRequest(375_000, "교재비 15,000원 추가"));

            // then
            assertThat(response.amount()).isEqualTo(375_000);
            assertThat(response.calculatedAmount()).isEqualTo(360_000);
            assertThat(invoice.getAmount()).isEqualTo(375_000);
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
            assertThat(invoice.getAdjustments()).singleElement().satisfies(adjustment -> {
                assertThat(adjustment.getBeforeAmount()).isEqualTo(360_000);
                assertThat(adjustment.getAfterAmount()).isEqualTo(375_000);
                assertThat(adjustment.getReason()).isEqualTo("교재비 15,000원 추가");
                assertThat(adjustment.getAdjustedBy()).isEqualTo(AdjustedBy.TEACHER);
            });
        }

        @Test
        @DisplayName("남의 과외 청구면 INVOICE404_1이고 리포트는 보지 않는다")
        void 남의_청구() {
            // given
            stubLockedInvoice(invoice(tutoring(OTHER_TEACHER_ID), 360_000));

            // when & then
            assertErrorCode(() -> reportInvoiceService.adjustAmount(TEACHER_ID, INVOICE_ID, request(375_000)),
                    "INVOICE404_1");
            verifyNoInteractions(settlementReportRepository);
        }

        @Test
        @DisplayName("삭제된 과외의 청구면 INVOICE404_1이다")
        void 삭제된_과외() {
            // given
            Tutoring tutoring = tutoring(TEACHER_ID);
            tutoring.delete(NOW);
            stubLockedInvoice(invoice(tutoring, 360_000));

            // when & then
            assertErrorCode(() -> reportInvoiceService.adjustAmount(TEACHER_ID, INVOICE_ID, request(375_000)),
                    "INVOICE404_1");
        }

        @Test
        @DisplayName("청구가 없으면 INVOICE404_1이다")
        void 청구_없음() {
            // given
            given(invoiceRepository.findByIdForUpdate(INVOICE_ID)).willReturn(Optional.empty());

            // when & then
            assertErrorCode(() -> reportInvoiceService.adjustAmount(TEACHER_ID, INVOICE_ID, request(375_000)),
                    "INVOICE404_1");
        }

        @Test
        @DisplayName("함께 보낸 리포트가 SENT면 INVOICE409_4이고 adjustAmount를 부르지 않는다")
        void 발송한_리포트() {
            // given
            Invoice invoice = spy(invoice(tutoring(TEACHER_ID), 360_000));
            stubLockedInvoice(invoice);
            stubReport(ReportStatus.SENT);

            // when & then
            assertErrorCode(() -> reportInvoiceService.adjustAmount(TEACHER_ID, INVOICE_ID, request(375_000)),
                    "INVOICE409_4");
            verify(invoice, never()).adjustAmount(anyInt(), anyString(), any(AdjustedBy.class));
            assertThat(invoice.getAmount()).isEqualTo(360_000);
        }

        @Test
        @DisplayName("리포트 없는 청구(report_id NULL)는 리포트를 보지 않고 수정한다")
        void 리포트_없는_청구() {
            // given
            Invoice invoice = invoice(tutoring(TEACHER_ID), 360_000);
            ReflectionTestUtils.setField(invoice, "reportId", null);
            stubLockedInvoice(invoice);

            // when
            ReportInvoiceResponse response =
                    reportInvoiceService.adjustAmount(TEACHER_ID, INVOICE_ID, request(375_000));

            // then
            assertThat(response.amount()).isEqualTo(375_000);
            verifyNoInteractions(settlementReportRepository);
        }

        @Test
        @DisplayName("이미 받은 금액보다 적게 바꾸면 INVOICE409_2다")
        void 입금_합계_미만() {
            // given
            Invoice invoice = invoice(tutoring(TEACHER_ID), 360_000);
            invoice.recordPayment(200_000, NOW);
            stubLockedInvoice(invoice);
            stubReport(ReportStatus.GENERATED);

            // when & then
            assertErrorCode(() -> reportInvoiceService.adjustAmount(TEACHER_ID, INVOICE_ID, request(150_000)),
                    "INVOICE409_2");
            assertThat(invoice.getAmount()).isEqualTo(360_000);
        }

        @Test
        @DisplayName("취소된 청구를 바꾸면 INVOICE409_1이다")
        void 취소된_청구() {
            // given
            Invoice invoice = invoice(tutoring(TEACHER_ID), 360_000);
            invoice.cancel(NOW);
            stubLockedInvoice(invoice);
            stubReport(ReportStatus.GENERATED);

            // when & then
            assertErrorCode(() -> reportInvoiceService.adjustAmount(TEACHER_ID, INVOICE_ID, request(375_000)),
                    "INVOICE409_1");
        }

        @Test
        @DisplayName("같은 금액이면 아무것도 바뀌지 않고 이력도 생기지 않는다")
        void 같은_금액() {
            // given
            Invoice invoice = invoice(tutoring(TEACHER_ID), 360_000);
            stubLockedInvoice(invoice);
            stubReport(ReportStatus.GENERATED);

            // when
            ReportInvoiceResponse response =
                    reportInvoiceService.adjustAmount(TEACHER_ID, INVOICE_ID, request(360_000));

            // then
            assertThat(response.amount()).isEqualTo(360_000);
            assertThat(invoice.getAdjustments()).isEmpty();
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        }

        @Test
        @DisplayName("확인 대기 중에 금액을 바꿔도 다 받은 게 아니면 확인 대기를 유지한다")
        void 확인_대기_유지() {
            // given
            Invoice invoice = invoice(tutoring(TEACHER_ID), 360_000);
            invoice.requestConfirm(Participant.createParent(null, ParticipantLabel.MOTHER, "01012345678"), NOW);
            stubLockedInvoice(invoice);
            stubReport(ReportStatus.GENERATED);

            // when
            reportInvoiceService.adjustAmount(TEACHER_ID, INVOICE_ID, request(375_000));

            // then
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CONFIRM_REQUESTED);
            assertThat(invoice.getAdjustments()).hasSize(1);
        }
    }

    private void stubLockedInvoice(Invoice invoice) {
        given(invoiceRepository.findByIdForUpdate(INVOICE_ID)).willReturn(Optional.of(invoice));
    }

    private static AdjustInvoiceAmountRequest request(int amount) {
        return new AdjustInvoiceAmountRequest(amount, "교재비 추가");
    }

    // 리포트 상태를 바꾸는 메서드가 없어(발송 기능 전) 목으로 상태만 정한다.
    // 다른 스텁의 willReturn 인자 안에서 목을 스텁하면 UnfinishedStubbing이 나므로 먼저 만든 뒤 넘긴다.
    private void stubReport(ReportStatus status) {
        SettlementReport report = mock(SettlementReport.class);
        given(report.getStatus()).willReturn(status);
        given(settlementReportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
    }

    private static Tutoring tutoring(Long teacherId) {
        Tutoring tutoring = Tutoring.builder()
                .teacher(teacher(teacherId))
                .subject(Subject.ENGLISH)
                .studentName("서아")
                .gradeLevel(10)
                .gradeSchoolYear(2026)
                .build();
        ReflectionTestUtils.setField(tutoring, "id", 100L);
        return tutoring;
    }

    // 프로필 PK는 @MapsId로 저장 시점에 users.id가 들어가므로, 소유자 비교용 ID는 테스트에서 직접 넣는다.
    private static TeacherProfile teacher(Long teacherId) {
        TeacherProfile teacher = TeacherProfile.create(
                User.createTeacher(Provider.KAKAO, "kakao-" + teacherId, "teacher" + teacherId + "@sudam.app"),
                "한서윤", List.of(Subject.ENGLISH), null);
        ReflectionTestUtils.setField(teacher, "id", teacherId);
        return teacher;
    }

    private static Invoice invoice(Tutoring tutoring, int amount) {
        TutoringPolicy policy = TutoringPolicy.builder()
                .tutoring(tutoring)
                .effectiveCycleNo(2)
                .settlementSessionCnt(8)
                .paymentTiming(PaymentTiming.PREPAID)
                .tuitionFee(amount)
                .bankCode("004")
                .bankName("국민")
                .accountNumber("123456-01-491492")
                .accountHolder("한서윤")
                .build();
        Invoice invoice = Invoice.issue(policy, 2L, REPORT_ID, InvoiceKind.NEXT, amount,
                InvoiceSnapshot.of(policy, 8, LocalDate.of(2026, 9, 14), List.of()));
        ReflectionTestUtils.setField(invoice, "id", INVOICE_ID);
        return invoice;
    }

    private static void assertErrorCode(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOf(ProjectException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo(code);
    }
}
