package com.sudam.domain.share.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.entity.InvoiceSnapshot;
import com.sudam.domain.invoice.enums.InvoiceKind;
import com.sudam.domain.invoice.repository.InvoiceRepository;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.NotificationConsent;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.participant.repository.ParticipantRepository;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.share.dto.request.CreateShareRequest;
import com.sudam.domain.share.dto.response.ShareResponse;
import com.sudam.domain.share.entity.LinkShare;
import com.sudam.domain.share.enums.ShareKind;
import com.sudam.domain.share.enums.ShareSourceType;
import com.sudam.domain.share.enums.ShareStatus;
import com.sudam.domain.share.repository.LinkShareRepository;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import com.sudam.global.exception.ProjectException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShareService 카카오톡 공유 정보 생성")
class ShareServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 21, 0);
    private static final Long TEACHER_ID = 1L;
    private static final Long OTHER_TEACHER_ID = 2L;
    private static final Long REPORT_ID = 21L;
    private static final Long INVOICE_ID = 12L;
    private static final Long PARENT_ID = 31L;
    private static final String TOKEN = "mother-token";

    @Mock private SettlementReportRepository settlementReportRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private LinkShareRepository linkShareRepository;

    private ShareService shareService;
    private Tutoring tutoring;
    private Participant parent;

    @BeforeEach
    void setUp() {
        shareService = new ShareService(settlementReportRepository, invoiceRepository, participantRepository,
                linkShareRepository, new ShareLinkFactory("https://sudam.app"),
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
        TeacherProfile teacher = withId(TeacherProfile.create(
                User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app"),
                "한서윤", List.of(Subject.ENGLISH), null), TEACHER_ID);
        tutoring = withId(Tutoring.builder().teacher(teacher).subject(Subject.ENGLISH)
                .studentName("윤서아").gradeLevel(9).gradeSchoolYear(2026).build(), 10L);
        parent = withId(Participant.createParent(tutoring, ParticipantLabel.MOTHER, "01000000002"), PARENT_ID);
        parent.issueLinkToken(TOKEN, NOW.minusDays(30));
    }

    @Nested
    @DisplayName("SETTLEMENT_REPORT")
    class SettlementReportShare {

        @Test
        @DisplayName("확인한 리포트면 학부모 리포트 URL과 수업 · 숙제 · 함께 보낸 청구로 만든 메시지를 돌려주고 REQUESTED를 남긴다")
        void 리포트_공유() {
            // given
            SettlementReport report = reviewedReport(88);
            given(settlementReportRepository.findWithTutoringById(REPORT_ID)).willReturn(Optional.of(report));
            given(participantRepository.findById(PARENT_ID)).willReturn(Optional.of(parent));
            given(invoiceRepository.findActiveByReportIdAndTeacherId(REPORT_ID, TEACHER_ID))
                    .willReturn(Optional.of(invoice(InvoiceKind.NEXT, 360_000)));
            givenSavedShareId(501L);

            // when
            ShareResponse response = shareService.create(TEACHER_ID,
                    new CreateShareRequest(ShareKind.SETTLEMENT_REPORT, REPORT_ID, PARENT_ID));

            // then
            assertThat(response).isEqualTo(new ShareResponse(501L,
                    "https://sudam.app/l/mother-token/reports/21",
                    "윤서아 학생 8회차 정산 리포트",
                    "수업 8/8회 · 숙제 수행률 88% · 다음 8회차 교육비 360,000원",
                    "링크로 보기"));
            LinkShare saved = savedShare();
            assertThat(saved.getKind()).isEqualTo(ShareKind.SETTLEMENT_REPORT);
            assertThat(saved.getSourceType()).isEqualTo(ShareSourceType.REPORT);
            assertThat(saved.getSourceId()).isEqualTo(REPORT_ID);
            assertThat(saved.getParticipant()).isSameAs(parent);
            assertThat(saved.getTutoring()).isSameAs(tutoring);
            assertThat(saved.getStatus()).isEqualTo(ShareStatus.REQUESTED);
            assertThat(saved.getRequestedAt()).isEqualTo(NOW);
            then(linkShareRepository).should().dismissRequested(ShareSourceType.REPORT, REPORT_ID, PARENT_ID);
        }

        @Test
        @DisplayName("숙제 결과와 함께 보낸 청구가 없으면 설명은 수업 회차만 담는다")
        void 숙제_청구_없음() {
            // given
            given(settlementReportRepository.findWithTutoringById(REPORT_ID))
                    .willReturn(Optional.of(reviewedReport(null)));
            given(participantRepository.findById(PARENT_ID)).willReturn(Optional.of(parent));
            given(invoiceRepository.findActiveByReportIdAndTeacherId(REPORT_ID, TEACHER_ID))
                    .willReturn(Optional.empty());
            givenSavedShareId(502L);

            // when
            ShareResponse response = shareService.create(TEACHER_ID,
                    new CreateShareRequest(ShareKind.SETTLEMENT_REPORT, REPORT_ID, PARENT_ID));

            // then
            assertThat(response.description()).isEqualTo("수업 8/8회");
        }

        @Test
        @DisplayName("코멘트를 확인하기 전이면 REPORT409_2이고 공유 기록을 남기지 않는다")
        void 확인_전_리포트() {
            // given
            SettlementReport report = report(88);
            given(settlementReportRepository.findWithTutoringById(REPORT_ID)).willReturn(Optional.of(report));

            // when & then
            assertErrorCode(() -> shareService.create(TEACHER_ID,
                    new CreateShareRequest(ShareKind.SETTLEMENT_REPORT, REPORT_ID, PARENT_ID)), "REPORT409_2");
            then(linkShareRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("다른 선생님의 리포트면 REPORT404_1")
        void 남의_리포트() {
            // given
            given(settlementReportRepository.findWithTutoringById(REPORT_ID))
                    .willReturn(Optional.of(reviewedReport(88)));

            // when & then
            assertErrorCode(() -> shareService.create(OTHER_TEACHER_ID,
                    new CreateShareRequest(ShareKind.SETTLEMENT_REPORT, REPORT_ID, PARENT_ID)), "REPORT404_1");
        }
    }

    @Nested
    @DisplayName("PAYMENT_RESEND")
    class PaymentResendShare {

        @Test
        @DisplayName("입금 대기 청구면 학부모 교육비 URL과 청구 이름 · 금액 메시지를 돌려준다")
        void 입금_대기_다시_안내() {
            // given
            Invoice invoice = invoice(InvoiceKind.NEXT, 360_000);
            given(invoiceRepository.findById(INVOICE_ID)).willReturn(Optional.of(invoice));
            given(participantRepository.findById(PARENT_ID)).willReturn(Optional.of(parent));
            givenSavedShareId(601L);

            // when
            ShareResponse response = shareService.create(TEACHER_ID,
                    new CreateShareRequest(ShareKind.PAYMENT_RESEND, INVOICE_ID, PARENT_ID));

            // then
            assertThat(response).isEqualTo(new ShareResponse(601L,
                    "https://sudam.app/l/mother-token/invoices/12",
                    "윤서아 학생 교육비 안내",
                    "다음 8회차 교육비 360,000원",
                    "교육비 보기"));
            assertThat(savedShare().getSourceType()).isEqualTo(ShareSourceType.INVOICE);
        }

        @Test
        @DisplayName("일부 입금이면 남은 금액을 덧붙인다")
        void 일부_입금() {
            // given
            Invoice invoice = invoice(InvoiceKind.NEXT, 360_000);
            invoice.recordPayment(120_000, NOW.minusDays(1));
            given(invoiceRepository.findById(INVOICE_ID)).willReturn(Optional.of(invoice));
            given(participantRepository.findById(PARENT_ID)).willReturn(Optional.of(parent));
            givenSavedShareId(602L);

            // when
            ShareResponse response = shareService.create(TEACHER_ID,
                    new CreateShareRequest(ShareKind.PAYMENT_RESEND, INVOICE_ID, PARENT_ID));

            // then
            assertThat(response.description()).isEqualTo("다음 8회차 교육비 360,000원 · 남은 금액 240,000원");
        }

        @Test
        @DisplayName("입금 완료된 청구면 INVOICE409_3")
        void 완납() {
            // given
            Invoice invoice = invoice(InvoiceKind.NEXT, 360_000);
            invoice.recordPayment(null, NOW.minusDays(1));
            given(invoiceRepository.findById(INVOICE_ID)).willReturn(Optional.of(invoice));

            // when & then
            assertErrorCode(() -> shareService.create(TEACHER_ID,
                    new CreateShareRequest(ShareKind.PAYMENT_RESEND, INVOICE_ID, PARENT_ID)), "INVOICE409_3");
        }
    }

    @Nested
    @DisplayName("받는 사람 · 요청 검증")
    class Recipient {

        @Test
        @DisplayName("다른 과외의 참여자면 SHARE404_1")
        void 다른_과외_참여자() {
            // given
            Tutoring other = withId(Tutoring.builder().subject(Subject.ENGLISH).studentName("박지후")
                    .gradeLevel(8).gradeSchoolYear(2026).build(), 11L);
            Participant otherParent = withId(Participant.createParent(other, ParticipantLabel.MOTHER, "01000000001"),
                    PARENT_ID);
            otherParent.issueLinkToken("other", NOW);
            given(invoiceRepository.findById(INVOICE_ID)).willReturn(Optional.of(invoice(InvoiceKind.NEXT, 360_000)));
            given(participantRepository.findById(PARENT_ID)).willReturn(Optional.of(otherParent));

            // when & then
            assertErrorCode(() -> shareService.create(TEACHER_ID,
                    new CreateShareRequest(ShareKind.PAYMENT_RESEND, INVOICE_ID, PARENT_ID)), "SHARE404_1");
        }

        @Test
        @DisplayName("링크 토큰이 없는(폐기된) 학부모면 SHARE404_1")
        void 링크_없음() {
            // given
            parent.revokeLinkToken();
            given(invoiceRepository.findById(INVOICE_ID)).willReturn(Optional.of(invoice(InvoiceKind.NEXT, 360_000)));
            given(participantRepository.findById(PARENT_ID)).willReturn(Optional.of(parent));

            // when & then
            assertErrorCode(() -> shareService.create(TEACHER_ID,
                    new CreateShareRequest(ShareKind.PAYMENT_RESEND, INVOICE_ID, PARENT_ID)), "SHARE404_1");
        }

        @Test
        @DisplayName("수신을 거부한 학부모면 SHARE409_1이고 공유 기록을 남기지 않는다")
        void 수신_거부() {
            // given
            ReflectionTestUtils.setField(parent, "notificationConsent", NotificationConsent.REJECTED);
            given(invoiceRepository.findById(INVOICE_ID)).willReturn(Optional.of(invoice(InvoiceKind.NEXT, 360_000)));
            given(participantRepository.findById(PARENT_ID)).willReturn(Optional.of(parent));

            // when & then
            assertErrorCode(() -> shareService.create(TEACHER_ID,
                    new CreateShareRequest(ShareKind.PAYMENT_RESEND, INVOICE_ID, PARENT_ID)), "SHARE409_1");
            then(linkShareRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("아직 지원하지 않는 종류(LESSON_UPDATE)면 SHARE400_1")
        void 지원하지_않는_종류() {
            // when & then
            assertErrorCode(() -> shareService.create(TEACHER_ID,
                    new CreateShareRequest(ShareKind.LESSON_UPDATE, 5L, PARENT_ID)), "SHARE400_1");
        }

        @Test
        @DisplayName("sourceId가 없으면 SHARE400_2")
        void sourceId_없음() {
            // when & then
            assertErrorCode(() -> shareService.create(TEACHER_ID,
                    new CreateShareRequest(ShareKind.SETTLEMENT_REPORT, null, PARENT_ID)), "SHARE400_2");
        }
    }

    private void givenSavedShareId(Long id) {
        given(linkShareRepository.save(any(LinkShare.class))).willAnswer(invocation -> withId(invocation.getArgument(0), id));
    }

    private LinkShare savedShare() {
        ArgumentCaptor<LinkShare> captor = ArgumentCaptor.forClass(LinkShare.class);
        then(linkShareRepository).should().save(captor.capture());
        return captor.getValue();
    }

    private SettlementReport report(Integer homeworkRate) {
        return withId(SettlementReport.builder().tutoring(tutoring)
                .cycle(withId(Cycle.open(tutoring, 1, LocalDateTime.of(2026, 9, 1, 0, 0)), 88L))
                .completedCount(8).targetCount(8).canceledCount(0).homeworkRate(homeworkRate)
                .understandingHigh(2).understandingMedium(5).understandingLow(1).build(), REPORT_ID);
    }

    private SettlementReport reviewedReport(Integer homeworkRate) {
        SettlementReport report = report(homeworkRate);
        report.reviewComment("이번 달 서아는 …", NOW.minusHours(1));
        return report;
    }

    private Invoice invoice(InvoiceKind kind, int amount) {
        TutoringPolicy policy = TutoringPolicy.builder().tutoring(tutoring).effectiveCycleNo(1)
                .settlementSessionCnt(8).paymentTiming(PaymentTiming.PREPAID).tuitionFee(amount)
                .bankCode("004").bankName("국민은행").accountNumber("000000-00-000001").accountHolder("한서윤").build();
        return withId(Invoice.issue(policy, 2L, REPORT_ID, kind, amount,
                InvoiceSnapshot.of(policy, 8, LocalDate.of(2026, 9, 1), List.of())), INVOICE_ID);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static void assertErrorCode(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOf(ProjectException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo(code);
    }
}
