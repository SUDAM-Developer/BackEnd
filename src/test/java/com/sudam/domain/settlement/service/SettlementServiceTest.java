package com.sudam.domain.settlement.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.enums.CycleStatus;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.invoice.entity.CanceledLesson;
import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.entity.InvoiceSnapshot;
import com.sudam.domain.invoice.enums.InvoiceKind;
import com.sudam.domain.invoice.repository.InvoiceRepository;
import com.sudam.domain.settlement.dto.SettlementResult;
import com.sudam.domain.settlement.port.CyclePeriod;
import com.sudam.domain.settlement.port.CycleSessionReader;
import com.sudam.domain.settlement.port.SettlementReportCreator;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.tutoring.repository.TutoringPolicyRepository;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementService 정산 실행")
class SettlementServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 19, 30);
    private static final Long TUTORING_ID = 1L;
    private static final Long CYCLE_ID = 10L;
    private static final Long NEXT_CYCLE_ID = 11L;
    private static final Long REPORT_ID = 20L;
    private static final Long INVOICE_ID = 30L;
    private static final int CYCLE_NO = 3;
    private static final List<CanceledLesson> CANCELED = List.of(
            new CanceledLesson(LocalDate.of(2026, 10, 22), "TEACHER"),
            new CanceledLesson(LocalDate.of(2026, 11, 5), "HOLIDAY")
    );

    @Mock
    private TutoringRepository tutoringRepository;
    @Mock
    private TutoringPolicyRepository tutoringPolicyRepository;
    @Mock
    private CycleRepository cycleRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private CycleSessionReader cycleSessionReader;
    @Mock
    private SettlementReportCreator settlementReportCreator;

    private SettlementService settlementService;
    private Tutoring tutoring;
    private Cycle cycle;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(ZonedDateTime.of(NOW, SEOUL).toInstant(), SEOUL);
        settlementService = new SettlementService(tutoringRepository, tutoringPolicyRepository, cycleRepository,
                invoiceRepository, cycleSessionReader, settlementReportCreator, clock);

        tutoring = Tutoring.builder()
                .teacherId(100L)
                .studentName("김서아")
                .gradeLevel(9)
                .gradeSchoolYear(2026)
                .build();
        ReflectionTestUtils.setField(tutoring, "id", TUTORING_ID);
        cycle = Cycle.open(tutoring, CYCLE_NO, NOW.minusWeeks(4));
        ReflectionTestUtils.setField(cycle, "id", CYCLE_ID);
    }

    @Test
    @DisplayName("기록 저장 트랜잭션 밖에서 부르지 못하도록 propagation = MANDATORY다")
    void 트랜잭션_전파는_MANDATORY다() throws NoSuchMethodException {
        // when
        Transactional transactional = SettlementService.class
                .getMethod("settleIfReached", Long.class)
                .getAnnotation(Transactional.class);

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    @DisplayName("인정 7회 · 정산 회차 8이면 정산하지 않고 사이클 · 리포트 · 청구가 그대로다")
    void 정산_회차에_못_미치면_정산하지_않는다() {
        // given
        givenLockedTutoringWithCycle(List.of(policy(1, 8, PaymentTiming.POSTPAID, 360_000, 1L)));
        given(cycleSessionReader.countRecognized(TUTORING_ID, CYCLE_ID)).willReturn(7);

        // when
        SettlementResult result = settlementService.settleIfReached(TUTORING_ID);

        // then
        assertThat(result).isEqualTo(SettlementResult.notSettled());
        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.IN_PROGRESS);
        verify(cycleRepository, never()).save(any());
        verifyNoInteractions(settlementReportCreator, invoiceRepository);
    }

    @Test
    @DisplayName("후불 8/8이면 사이클 정산 → 다음 사이클 열기 → 리포트 → 청구 순서로 처리하고 수업료 그대로 이번 사이클에 청구한다")
    void 후불_정산은_이번_사이클에_수업료를_청구한다() {
        // given
        TutoringPolicy policy = policy(1, 8, PaymentTiming.POSTPAID, 360_000, 1L);
        givenLockedTutoringWithCycle(List.of(policy));
        given(cycleSessionReader.countRecognized(TUTORING_ID, CYCLE_ID)).willReturn(8);
        given(cycleSessionReader.period(TUTORING_ID, CYCLE_ID))
                .willReturn(new CyclePeriod(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 12)));
        given(cycleSessionReader.canceledIn(TUTORING_ID, CYCLE_ID)).willReturn(CANCELED);
        givenSavesAssignIds();

        // when
        SettlementResult result = settlementService.settleIfReached(TUTORING_ID);

        // then
        assertThat(result).isEqualTo(new SettlementResult(true, CYCLE_ID, REPORT_ID, INVOICE_ID));
        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.SETTLED);
        assertThat(cycle.getSettledAt()).isEqualTo(NOW);

        InOrder order = inOrder(tutoringRepository, cycleRepository, settlementReportCreator, invoiceRepository);
        order.verify(tutoringRepository).findByIdForUpdate(TUTORING_ID);
        ArgumentCaptor<Cycle> nextCycle = ArgumentCaptor.forClass(Cycle.class);
        order.verify(cycleRepository).save(nextCycle.capture());
        order.verify(settlementReportCreator).create(TUTORING_ID, CYCLE_ID);
        ArgumentCaptor<Invoice> invoice = ArgumentCaptor.forClass(Invoice.class);
        order.verify(invoiceRepository).save(invoice.capture());

        assertThat(nextCycle.getValue().getCycleNo()).isEqualTo(CYCLE_NO + 1);
        assertThat(nextCycle.getValue().isInProgress()).isTrue();
        assertThat(nextCycle.getValue().getStartedAt()).isEqualTo(NOW);
        assertThat(nextCycle.getValue().getTutoring()).isSameAs(tutoring);

        Invoice issued = invoice.getValue();
        assertThat(issued.getKind()).isEqualTo(InvoiceKind.CURRENT);
        assertThat(issued.getAmount()).isEqualTo(360_000);
        assertThat(issued.getBilledCycleId()).isEqualTo(CYCLE_ID);
        assertThat(issued.getReportId()).isEqualTo(REPORT_ID);
        assertThat(issued.getPolicy()).isSameAs(policy);
        assertThat(issued.getSnapshot()).isEqualTo(new InvoiceSnapshot(
                8, 45_000, 8, PaymentTiming.POSTPAID, LocalDate.of(2026, 10, 15), CANCELED));
    }

    @Test
    @DisplayName("선불 8/8이고 다음 사이클부터 수업료 400,000원이 예약돼 있으면 새 사이클에 400,000원을 NEXT로 청구한다")
    void 선불_정산은_새_사이클_정책으로_다음_사이클에_청구한다() {
        // given
        TutoringPolicy current = policy(1, 8, PaymentTiming.PREPAID, 360_000, 1L);
        TutoringPolicy reserved = policy(CYCLE_NO + 1, 8, PaymentTiming.PREPAID, 400_000, 2L);
        givenLockedTutoringWithCycle(List.of(current, reserved));
        given(cycleSessionReader.countRecognized(TUTORING_ID, CYCLE_ID)).willReturn(8);
        given(cycleSessionReader.canceledIn(TUTORING_ID, CYCLE_ID)).willReturn(CANCELED);
        givenSavesAssignIds();

        // when
        SettlementResult result = settlementService.settleIfReached(TUTORING_ID);

        // then
        assertThat(result).isEqualTo(new SettlementResult(true, CYCLE_ID, REPORT_ID, INVOICE_ID));
        ArgumentCaptor<Invoice> invoice = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoice.capture());

        Invoice issued = invoice.getValue();
        assertThat(issued.getKind()).isEqualTo(InvoiceKind.NEXT);
        assertThat(issued.getAmount()).isEqualTo(400_000);
        assertThat(issued.getBilledCycleId()).isEqualTo(NEXT_CYCLE_ID);
        assertThat(issued.getReportId()).isEqualTo(REPORT_ID);
        assertThat(issued.getPolicy()).isSameAs(reserved);
        assertThat(issued.getSnapshot()).isEqualTo(new InvoiceSnapshot(
                null, 50_000, 8, PaymentTiming.PREPAID, null, CANCELED));
        // 선불은 새 사이클이 아직 시작 전이라 기간을 읽지 않는다.
        verify(cycleSessionReader, never()).period(any(), any());
    }

    @Test
    @DisplayName("과외가 종료(ENDED)됐으면 정산하지 않는다")
    void 종료된_과외는_정산하지_않는다() {
        // given
        tutoring.end(NOW.minusDays(1));
        given(tutoringRepository.findByIdForUpdate(TUTORING_ID)).willReturn(Optional.of(tutoring));

        // when
        SettlementResult result = settlementService.settleIfReached(TUTORING_ID);

        // then
        assertThat(result.settled()).isFalse();
        verifyNoInteractions(cycleRepository, cycleSessionReader, settlementReportCreator, invoiceRepository);
    }

    @Test
    @DisplayName("과외가 없거나 삭제됐으면 정산하지 않는다")
    void 없거나_삭제된_과외는_정산하지_않는다() {
        // given
        Tutoring deleted = Tutoring.builder().teacherId(100L).gradeLevel(9).gradeSchoolYear(2026).build();
        deleted.delete(NOW.minusDays(1));
        given(tutoringRepository.findByIdForUpdate(TUTORING_ID)).willReturn(Optional.of(deleted));
        given(tutoringRepository.findByIdForUpdate(2L)).willReturn(Optional.empty());

        // when
        SettlementResult deletedResult = settlementService.settleIfReached(TUTORING_ID);
        SettlementResult missingResult = settlementService.settleIfReached(2L);

        // then
        assertThat(deletedResult.settled()).isFalse();
        assertThat(missingResult.settled()).isFalse();
        verifyNoInteractions(cycleRepository, cycleSessionReader, settlementReportCreator, invoiceRepository);
    }

    @Test
    @DisplayName("진행 중 사이클이 없으면 정산하지 않는다")
    void 진행_중_사이클이_없으면_정산하지_않는다() {
        // given
        given(tutoringRepository.findByIdForUpdate(TUTORING_ID)).willReturn(Optional.of(tutoring));
        given(cycleRepository.findByTutoringIdAndStatus(TUTORING_ID, CycleStatus.IN_PROGRESS))
                .willReturn(Optional.empty());

        // when
        SettlementResult result = settlementService.settleIfReached(TUTORING_ID);

        // then
        assertThat(result.settled()).isFalse();
        verify(cycleRepository, never()).save(any());
        verifyNoInteractions(cycleSessionReader, settlementReportCreator, invoiceRepository);
    }

    @Test
    @DisplayName("리포트 생성이 예외를 던지면 잡지 않고 그대로 올리고 청구를 저장하지 않는다")
    void 리포트_생성_실패는_그대로_올라간다() {
        // given
        givenLockedTutoringWithCycle(List.of(policy(1, 8, PaymentTiming.POSTPAID, 360_000, 1L)));
        given(cycleSessionReader.countRecognized(TUTORING_ID, CYCLE_ID)).willReturn(8);
        given(cycleRepository.save(any(Cycle.class))).willAnswer(invocation -> invocation.getArgument(0));
        IllegalStateException failure = new IllegalStateException("리포트 저장 실패");
        given(settlementReportCreator.create(TUTORING_ID, CYCLE_ID)).willThrow(failure);

        // when & then
        assertThatThrownBy(() -> settlementService.settleIfReached(TUTORING_ID)).isSameAs(failure);
        verify(invoiceRepository, never()).save(any());
    }

    private void givenLockedTutoringWithCycle(List<TutoringPolicy> policies) {
        given(tutoringRepository.findByIdForUpdate(TUTORING_ID)).willReturn(Optional.of(tutoring));
        given(cycleRepository.findByTutoringIdAndStatus(TUTORING_ID, CycleStatus.IN_PROGRESS))
                .willReturn(Optional.of(cycle));
        given(tutoringPolicyRepository.findAllByTutoringId(TUTORING_ID)).willReturn(policies);
    }

    // IDENTITY 전략이라 save 직후 ID가 생기는 것을 흉내 낸다.
    private void givenSavesAssignIds() {
        given(cycleRepository.save(any(Cycle.class))).willAnswer(invocation -> {
            Cycle saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", NEXT_CYCLE_ID);
            return saved;
        });
        given(settlementReportCreator.create(TUTORING_ID, CYCLE_ID)).willReturn(REPORT_ID);
        given(invoiceRepository.save(any(Invoice.class))).willAnswer(invocation -> {
            Invoice saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", INVOICE_ID);
            return saved;
        });
    }

    private TutoringPolicy policy(int effectiveCycleNo, int settlementSessionCnt, PaymentTiming timing,
                                  int tuitionFee, Long id) {
        TutoringPolicy policy = TutoringPolicy.builder()
                .tutoring(tutoring)
                .effectiveCycleNo(effectiveCycleNo)
                .settlementSessionCnt(settlementSessionCnt)
                .paymentTiming(timing)
                .tuitionFee(tuitionFee)
                .bankCode("004")
                .bankName("국민")
                .accountNumber("123456-01-491492")
                .accountHolder("한서윤")
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }
}
