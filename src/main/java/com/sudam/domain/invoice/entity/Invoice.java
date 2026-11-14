package com.sudam.domain.invoice.entity;

import com.sudam.domain.invoice.enums.AdjustedBy;
import com.sudam.domain.invoice.enums.InvoiceKind;
import com.sudam.domain.invoice.enums.InvoiceStatus;
import com.sudam.domain.invoice.exception.InvoiceException;
import com.sudam.domain.invoice.exception.code.InvoiceErrorCode;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.global.crypto.AccountNumberConverter;
import com.sudam.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 교육비 청구. 정산 실행 · 선불 첫 청구 · 수업 종료 때 Invoice.issue로 만든다.
 * 계산 근거(snapshot)와 계좌는 청구 시점 값을 복사해 두므로 이후 정책이 바뀌어도 이 청구는 그대로다.
 *
 * <p>입금 행 추가 · 취소와 상태 변경은 같은 트랜잭션이어야 한다 (ERD 7. 입금 상태).
 * 상태를 바꾸는 메서드는 모두 {@code InvoiceRepository.findByIdForUpdate}로 청구 행을 잠근 뒤 호출한다.
 *
 * <p>'취소되지 않은 청구는 사이클당 1개'(active_cycle_key 생성 열)는 P0에서 DB로 보장하지 않는다.
 * 정산 실행이 진행 중 사이클을 잠그고 청구를 만드는 것으로 대신한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "invoices",
        indexes = {
                @Index(name = "idx_invoices_tutoring_status", columnList = "tutoring_id, status"),
                @Index(name = "idx_invoices_status_notified", columnList = "status, notified_at")
        }
)
public class Invoice extends BaseTimeEntity {

    private static final int ACCOUNT_LAST_DIGITS = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutoring_id", nullable = false)
    private Tutoring tutoring;

    // 청구 대상 사이클 (선불은 다음 사이클). cycles 엔티티가 생기기 전이라 연관관계 없는 숫자 열이다.
    @Column(name = "billed_cycle_id", nullable = false)
    private Long billedCycleId;

    // 함께 발송된 정산 리포트. 선불 첫 청구 · 리포트 없는 종료는 NULL. settlement_reports 엔티티가 생기기 전이라 숫자 열이다.
    @Column(name = "report_id")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private TutoringPolicy policy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceKind kind;

    // 이하 정책 스냅샷.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentTiming paymentTiming;

    @Column(nullable = false)
    private Integer tuitionFee;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer settlementSessionCnt;

    // 회당 단가 = 수업료 ÷ 정산 회차, 원 미만 버림.
    @Column(nullable = false)
    private Integer unitPrice;

    // 후불 · 종료 청구의 인정 회차. 선불은 NULL.
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer recognizedCount;

    // 자동 계산 금액. 금액을 수정해도 바뀌지 않는다.
    @Column(nullable = false)
    private Integer calculatedAmount;

    // 최종 청구 금액 (수정 반영).
    @Column(nullable = false)
    private Integer amount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private InvoiceSnapshot snapshot;

    // 선불 중도 종료 시 '직접 돌려 드릴' 금액 (표시용, 7.1.2).
    private Integer refundableAmount;

    // 이하 청구 시점 계좌 스냅샷 (정책에서 복사).
    @Column(columnDefinition = "CHAR(3)")
    private String bankCode;

    @Column(nullable = false, length = 20)
    private String bankName;

    @Convert(converter = AccountNumberConverter.class)
    @Column(nullable = false, length = 255)
    private String accountNumber;

    @Column(nullable = false, columnDefinition = "CHAR(4)")
    private String accountLast4;

    @Column(nullable = false, length = 20)
    private String accountHolder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    // [입금했어요]를 먼저 누른 학부모.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirm_requested_by")
    private Participant confirmRequestedBy;

    private LocalDateTime confirmRequestedAt;

    // 교육비 안내를 처음 보낸 시각 (웹훅). '이번 달' 판단 기준.
    private LocalDateTime notifiedAt;

    // [카톡으로 입금 안내 다시 보내기] 마지막 시각.
    private LocalDateTime renotifiedAt;

    // 선생님 미입금 알림 1회 (6.3.2).
    private LocalDateTime reminderSentAt;

    private LocalDateTime canceledAt;

    // 입금 · 수정 이력은 청구에 종속된다. 삭제 대신 canceled_at을 쓰므로 PERSIST만 전파한다.
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.PERSIST)
    private List<InvoicePayment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.PERSIST)
    private List<InvoiceAmountAdjustment> adjustments = new ArrayList<>();

    private Invoice(TutoringPolicy policy, Long billedCycleId, Long reportId, InvoiceKind kind, int amount,
                    InvoiceSnapshot snapshot) {
        String accountNumber = policy.getAccountNumber();
        this.tutoring = policy.getTutoring();
        this.billedCycleId = billedCycleId;
        this.reportId = reportId;
        this.policy = policy;
        this.kind = kind;
        this.paymentTiming = policy.getPaymentTiming();
        this.tuitionFee = policy.getTuitionFee();
        this.settlementSessionCnt = policy.getSettlementSessionCnt();
        this.unitPrice = unitPriceOf(policy);
        this.recognizedCount = snapshot.recognizedCount();
        this.calculatedAmount = amount;
        this.amount = amount;
        this.snapshot = snapshot;
        this.bankCode = policy.getBankCode();
        this.bankName = policy.getBankName();
        this.accountNumber = accountNumber;
        this.accountLast4 = accountNumber.substring(accountNumber.length() - ACCOUNT_LAST_DIGITS);
        this.accountHolder = policy.getAccountHolder();
        this.status = InvoiceStatus.PENDING;
    }

    /***
     * 함수 기능: 교육비 청구를 만든다. 과외 · 선불/후불 · 수업료 · 정산 회차 · 회당 단가 · 계좌는 정책에서 복사하고,
     *          상태는 PENDING, 자동 계산 금액과 최종 금액은 같은 값으로 시작한다.
     *          계좌번호는 정책에서 복호화된 원문을 복사하고 저장할 때 AccountNumberConverter가 다시 암호화한다.
     * @param policy        이 청구에 적용한 정책 (후불은 이번 사이클, 선불은 다음 사이클 정책)
     * @param billedCycleId 청구 대상 사이클 ID
     * @param reportId      함께 발송할 정산 리포트 ID. 없으면 NULL
     * @param kind          수입 탭 이름 (FIRST / NEXT / CURRENT / FINAL)
     * @param amount        자동 계산 금액 (원)
     * @param snapshot      계산 근거. recognized_count 컬럼도 여기서 복사한다
     * @return 저장 전 Invoice
     */
    public static Invoice issue(TutoringPolicy policy, Long billedCycleId, Long reportId, InvoiceKind kind,
                                int amount, InvoiceSnapshot snapshot) {
        return new Invoice(policy, billedCycleId, reportId, kind, amount, snapshot);
    }

    // 회당 단가 = 수업료 ÷ 정산 회차, 원 미만 버림. 스냅샷과 컬럼이 같은 계산을 쓰도록 한 곳에 둔다.
    public static int unitPriceOf(TutoringPolicy policy) {
        return policy.getTuitionFee() / policy.getSettlementSessionCnt();
    }

    /***
     * 함수 기능: 선생님이 확인한 입금을 반영한다 ([전액 입금 완료] · 일부 입금, 6.3.1).
     *          요청 금액이 없으면 잔액 전부, 잔액보다 크면 잔액까지만 반영한다.
     *          합계가 청구 금액 이상이면 PAID, 아니면 PARTIAL. 확인 요청 기록(confirm_requested_*)은 지우지 않는다.
     *          청구 행을 잠근 뒤 호출한다.
     * @param amount      확인한 금액. NULL이면 잔액 전부
     * @param confirmedAt 입금 확인 시각 (수입 탭 월별 합계 기준)
     * @return 반영한 금액과 새 입금 행
     * @throws InvoiceException 취소된 청구면 INVOICE409_1, 이미 완납이면 INVOICE409_3
     */
    public RecordedPayment recordPayment(Integer amount, LocalDateTime confirmedAt) {
        validateNotCanceled();
        int balance = balance();
        if (balance <= 0) {
            throw new InvoiceException(InvoiceErrorCode.ALREADY_PAID);
        }
        if (amount != null && amount <= 0) {
            throw new IllegalArgumentException("입금 금액은 1원 이상이어야 합니다.");
        }

        int applied = amount == null ? balance : Math.min(amount, balance);
        InvoicePayment payment = new InvoicePayment(this, applied, confirmedAt);
        payments.add(payment);
        this.status = paidAmount() >= this.amount ? InvoiceStatus.PAID : InvoiceStatus.PARTIAL;
        return new RecordedPayment(applied, payment);
    }

    /***
     * 함수 기능: 잘못 확인한 입금을 되돌린다. 입금 행에 canceled_at을 남기고 합계에 맞춰 상태를 다시 정한다.
     *          확인 대기(CONFIRM_REQUESTED)는 다 받은 상태가 아니면 그대로 둔다. 이미 되돌린 입금이면 아무것도 하지 않는다.
     *          청구 행을 잠근 뒤 호출한다.
     * @param payment    이 청구의 입금 행
     * @param canceledAt 되돌린 시각
     * @throws InvoiceException 취소된 청구면 INVOICE409_1
     */
    public void cancelPayment(InvoicePayment payment, LocalDateTime canceledAt) {
        validateNotCanceled();
        if (payment.getInvoice() != this) {
            throw new IllegalArgumentException("이 청구의 입금이 아닙니다.");
        }
        if (payment.isCanceled()) {
            return;
        }
        payment.cancel(canceledAt);
        recalculateStatusKeepingConfirmRequest();
    }

    /***
     * 함수 기능: 학부모가 [입금했어요]를 눌러 입금 확인을 요청한다. PENDING · PARTIAL에서만 가능하고,
     *          이미 확인 대기면 먼저 누른 한 명만 남도록 막는다. 청구 행을 잠근 뒤 호출한다.
     * @param requester   요청한 학부모
     * @param requestedAt 요청 시각
     * @throws InvoiceException PENDING · PARTIAL이 아니면 INVOICE409_5
     */
    public void requestConfirm(Participant requester, LocalDateTime requestedAt) {
        if (status != InvoiceStatus.PENDING && status != InvoiceStatus.PARTIAL) {
            throw new InvoiceException(InvoiceErrorCode.CONFIRM_NOT_REQUESTABLE);
        }
        this.status = InvoiceStatus.CONFIRM_REQUESTED;
        this.confirmRequestedBy = requester;
        this.confirmRequestedAt = requestedAt;
    }

    /***
     * 함수 기능: 선생님이 [아직 안 들어옴]을 눌러 확인 요청을 거절한다.
     *          입금 합계에 따라 PENDING 또는 PARTIAL로 되돌리고 confirm_requested_*를 비운다.
     *          확인 대기가 아니면(중복 클릭 등) 아무것도 하지 않는다. 청구 행을 잠근 뒤 호출한다.
     * @throws InvoiceException 취소된 청구면 INVOICE409_1
     */
    public void rejectConfirm() {
        validateNotCanceled();
        if (status != InvoiceStatus.CONFIRM_REQUESTED) {
            return;
        }
        this.confirmRequestedBy = null;
        this.confirmRequestedAt = null;
        this.status = statusByPaidAmount();
    }

    /***
     * 함수 기능: 청구 금액을 바꾸고 수정 이력을 남긴다 (6.1.3, 수업 종료 7.1.2).
     *          금액이 같으면 아무것도 하지 않는다. 바뀌면 합계에 맞춰 상태를 다시 정하되,
     *          확인 대기(CONFIRM_REQUESTED)는 바뀐 금액을 다 받은 경우가 아니면 그대로 둔다.
     *
     *          호출 규칙 (교육비 수정 API · 수업 종료 처리가 지킨다):
     *          1. InvoiceRepository.findByIdForUpdate로 청구 행을 잠근 뒤 호출한다.
     *          2. 함께 보낸 정산 리포트(report_id)가 SENT면 호출하기 전에 INVOICE409_4를 던진다.
     *             리포트 엔티티가 이 도메인 밖이라 이 메서드는 리포트 발송 여부를 검사하지 않는다.
     * @param newAmount 바꿀 금액 (원)
     * @param reason    수정 사유. 교육비 카드에 '금액 수정 사유'로 표시된다
     * @param by        TEACHER = 선생님 수정, SYSTEM = 수업 종료 자동 변경
     * @return 새 수정 이력 행. 금액이 같아 바꾸지 않았으면 빈 값
     * @throws InvoiceException 취소된 청구면 INVOICE409_1, 이미 받은 금액보다 적으면 INVOICE409_2
     */
    public Optional<InvoiceAmountAdjustment> adjustAmount(int newAmount, String reason, AdjustedBy by) {
        validateNotCanceled();
        if (newAmount < 0) {
            throw new IllegalArgumentException("교육비는 0원 이상이어야 합니다.");
        }
        if (newAmount < paidAmount()) {
            throw new InvoiceException(InvoiceErrorCode.AMOUNT_BELOW_PAID);
        }
        if (newAmount == this.amount) {
            return Optional.empty();
        }

        InvoiceAmountAdjustment adjustment = new InvoiceAmountAdjustment(this, this.amount, newAmount, reason, by);
        adjustments.add(adjustment);
        this.amount = newAmount;
        recalculateStatusKeepingConfirmRequest();
        return Optional.of(adjustment);
    }

    /***
     * 함수 기능: 청구를 취소한다. 취소 후에는 같은 사이클로 다시 청구할 수 있다. 이미 취소됐으면 아무것도 하지 않는다.
     * @param canceledAt 취소 시각
     */
    public void cancel(LocalDateTime canceledAt) {
        if (status == InvoiceStatus.CANCELED) {
            return;
        }
        this.status = InvoiceStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    // 입금 · 수정 이력은 이 엔티티의 메서드로만 늘어나야 하므로 읽기 전용으로 내보낸다.
    public List<InvoicePayment> getPayments() {
        return Collections.unmodifiableList(payments);
    }

    public List<InvoiceAmountAdjustment> getAdjustments() {
        return Collections.unmodifiableList(adjustments);
    }

    // 취소되지 않은 입금의 합계.
    public int paidAmount() {
        return payments.stream()
                .filter(payment -> !payment.isCanceled())
                .mapToInt(InvoicePayment::getAmount)
                .sum();
    }

    // 남은 금액. 다 받았으면 0 이하.
    public int balance() {
        return amount - paidAmount();
    }

    private void validateNotCanceled() {
        if (status == InvoiceStatus.CANCELED) {
            throw new InvoiceException(InvoiceErrorCode.INVOICE_CANCELED);
        }
    }

    // 확인 요청을 고려하지 않은 입금 합계 기준 상태.
    private InvoiceStatus statusByPaidAmount() {
        int paid = paidAmount();
        if (paid >= amount) {
            return InvoiceStatus.PAID;
        }
        return paid > 0 ? InvoiceStatus.PARTIAL : InvoiceStatus.PENDING;
    }

    // 확인 대기 중에는 학부모 요청을 지우지 않는다. 다 받았으면 확인할 것이 없으므로 PAID로 바꾼다.
    private void recalculateStatusKeepingConfirmRequest() {
        InvoiceStatus recalculated = statusByPaidAmount();
        if (status == InvoiceStatus.CONFIRM_REQUESTED && recalculated != InvoiceStatus.PAID) {
            return;
        }
        this.status = recalculated;
    }
}
