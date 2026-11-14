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
import com.sudam.domain.settlement.exception.SettlementException;
import com.sudam.domain.settlement.exception.code.SettlementErrorCode;
import com.sudam.domain.settlement.port.CycleSessionReader;
import com.sudam.domain.settlement.port.SettlementReportCreator;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicies;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.tutoring.enums.TutoringStatus;
import com.sudam.domain.tutoring.repository.TutoringPolicyRepository;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 정산 실행 (6.1.1). 정산 회차를 채운 기록 저장 트랜잭션 안에서 사이클을 닫고 다음 사이클을 열고, 리포트와 청구를 만든다.
 * 회차 숫자와 리포트 생성은 회차 · 리포트 담당 쪽 구현(CycleSessionReader, SettlementReportCreator)에 맡긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private final TutoringRepository tutoringRepository;
    private final TutoringPolicyRepository tutoringPolicyRepository;
    private final CycleRepository cycleRepository;
    private final InvoiceRepository invoiceRepository;
    private final CycleSessionReader cycleSessionReader;
    private final SettlementReportCreator settlementReportCreator;
    private final Clock clock;

    /***
     * 함수 기능: 진행 중 사이클이 정산 회차를 채웠으면 정산한다.
     *          과외 행 잠금 → 진행 중 사이클 → 이 사이클 정책 → 인정 회차 확인 → 사이클 정산 · 다음 사이클 열기
     *          → 리포트 생성 → 청구 생성 순서다. 리포트 · 청구 실패는 잡지 않아 기록 저장까지 전부 롤백된다.
     *          기록 저장 트랜잭션 안에서만 부를 수 있다 (MANDATORY).
     * @param tutoringId 과외 ID
     * @return 정산 결과. 과외가 없거나 삭제 · 종료됐거나, 진행 중 사이클이 없거나, 회차가 모자라면 settled = false
     * @throws SettlementException 사이클에 적용할 정책이 없으면 SETTLEMENT500_1
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SettlementResult settleIfReached(Long tutoringId) {
        // 사이클을 열고 닫는 모든 쪽이 과외 행을 먼저 잠그므로, 동시에 정산이 두 번 돌아도 진행 중 사이클이 둘이 되지 않는다.
        Optional<Tutoring> locked = tutoringRepository.findByIdForUpdate(tutoringId)
                .filter(tutoring -> !tutoring.isDeleted() && tutoring.getStatus() != TutoringStatus.ENDED);
        if (locked.isEmpty()) {
            return SettlementResult.notSettled();
        }
        Tutoring tutoring = locked.get();

        Optional<Cycle> inProgress = cycleRepository.findByTutoringIdAndStatus(tutoringId, CycleStatus.IN_PROGRESS);
        if (inProgress.isEmpty()) {
            return SettlementResult.notSettled();
        }
        Cycle cycle = inProgress.get();
        int cycleNo = cycle.getCycleNo();

        List<TutoringPolicy> policies = tutoringPolicyRepository.findAllByTutoringId(tutoringId);
        TutoringPolicy policy = policyFor(policies, cycleNo);

        int recognized = cycleSessionReader.countRecognized(tutoringId, cycle.getId());
        int target = policy.getSettlementSessionCnt();
        if (recognized < target) {
            return SettlementResult.notSettled();
        }
        if (recognized > target) {
            // 정산 회차 축소(P1) 전에는 생기지 않는다. 넘치는 회차를 옮기는 처리는 P1에서 넣고, 지금은 그대로 정산한다.
            log.warn("[정산] 인정 회차가 정산 회차보다 많습니다. tutoringId={}, cycleId={}, recognized={}, target={}",
                    tutoringId, cycle.getId(), recognized, target);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        cycle.settle(now);
        Cycle next = cycleRepository.save(Cycle.open(tutoring, cycleNo + 1, now));

        // 리포트를 먼저 만든다. 리포트는 청구 ID를 갖지 않고, 리포트 쪽은 invoices.report_id로 청구를 찾는다.
        Long reportId = settlementReportCreator.create(tutoringId, cycle.getId());

        Invoice invoice = policy.getPaymentTiming() == PaymentTiming.POSTPAID
                ? postpaidInvoice(tutoringId, cycle, policy, recognized, reportId)
                : prepaidInvoice(tutoringId, cycle, next, policies, reportId);
        Invoice saved = invoiceRepository.save(invoice);

        return SettlementResult.settled(cycle.getId(), reportId, saved.getId());
    }

    // 후불: 이번 사이클 · 이번 정책. 회차를 다 채웠으니 수업료 그대로 (회차 × 단가는 단가 버림 때문에 수업료보다 작을 수 있다).
    private Invoice postpaidInvoice(Long tutoringId, Cycle cycle, TutoringPolicy policy, int recognized,
                                    Long reportId) {
        InvoiceSnapshot snapshot = InvoiceSnapshot.of(
                policy,
                recognized,
                cycleSessionReader.period(tutoringId, cycle.getId()).start(),
                cycleSessionReader.canceledIn(tutoringId, cycle.getId())
        );
        return Invoice.issue(policy, cycle.getId(), reportId, InvoiceKind.CURRENT, policy.getTuitionFee(), snapshot);
    }

    // 선불: 새 사이클 · 새 사이클 번호의 정책(예약된 수업료 변경 반영). 새 사이클은 아직 시작 전이라 인정 회차 · 시작일이 없고,
    // 휴강 내역은 방금 닫은 사이클 것을 보여준다.
    private Invoice prepaidInvoice(Long tutoringId, Cycle closed, Cycle next, List<TutoringPolicy> policies,
                                   Long reportId) {
        TutoringPolicy nextPolicy = policyFor(policies, next.getCycleNo());
        List<CanceledLesson> canceled = cycleSessionReader.canceledIn(tutoringId, closed.getId());
        InvoiceSnapshot snapshot = InvoiceSnapshot.of(nextPolicy, null, null, canceled);
        return Invoice.issue(nextPolicy, next.getId(), reportId, InvoiceKind.NEXT, nextPolicy.getTuitionFee(),
                snapshot);
    }

    private TutoringPolicy policyFor(List<TutoringPolicy> policies, int cycleNo) {
        return TutoringPolicies.applicableTo(policies, cycleNo)
                .orElseThrow(() -> new SettlementException(SettlementErrorCode.POLICY_NOT_FOUND));
    }
}
