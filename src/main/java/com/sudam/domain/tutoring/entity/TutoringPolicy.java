package com.sudam.domain.tutoring.entity;

import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.global.crypto.AccountNumberConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 사이클 기준 과외 정책 이력. 바꿀 때는 수정하지 않고 새 행을 추가한다.
 * 사이클 N에 적용할 행은 TutoringPolicies.applicableTo로 고른다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "tutoring_policies",
        indexes = @Index(
                name = "idx_tutoring_policies_tutoring_cycle",
                columnList = "tutoring_id, effective_cycle_no, id"
        )
)
public class TutoringPolicy {

    private static final int ACCOUNT_LAST_DIGITS = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutoring_id", nullable = false)
    private Tutoring tutoring;

    // 적용 시작 사이클 번호.
    @Column(nullable = false)
    private Integer effectiveCycleNo;

    // 정산 회차 1~20.
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer settlementSessionCnt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentTiming paymentTiming;

    // 사이클 1번의 수업료 (원).
    @Column(nullable = false)
    private Integer tuitionFee;

    // 학생 당일 취소 인정 여부. P0에서는 항상 false (팀 결정).
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean sameDayCancelRecognized;

    // 금융결제원 3자리. 은행 목록에 없는 이름('기타')이면 NULL.
    @Column(columnDefinition = "CHAR(3)")
    private String bankCode;

    // 목록 이름 또는 '기타' 직접 입력.
    @Column(nullable = false, length = 20)
    private String bankName;

    // 숫자만 남긴 계좌번호를 AES-GCM으로 암호화해 저장한다.
    @Convert(converter = AccountNumberConverter.class)
    @Column(nullable = false, length = 255)
    private String accountNumber;

    // 목록에서 복호화하지 않도록 뒤 4자리를 따로 둔다.
    @Column(nullable = false, columnDefinition = "CHAR(4)")
    private String accountLast4;

    @Column(nullable = false, length = 20)
    private String accountHolder;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /***
     * 함수 기능: 정책 이력 행을 만든다. 계좌번호는 하이픈을 지운 숫자만 저장하고 뒤 4자리를 따로 둔다.
     *          계좌번호 자릿수(10~14) 등 형식 검증은 요청 검증에서 끝났다는 전제다.
     */
    @Builder
    private TutoringPolicy(Tutoring tutoring, Integer effectiveCycleNo, Integer settlementSessionCnt,
                           PaymentTiming paymentTiming, Integer tuitionFee, String bankCode, String bankName,
                           String accountNumber, String accountHolder) {
        String digits = accountNumber.replace("-", "");
        this.tutoring = tutoring;
        this.effectiveCycleNo = effectiveCycleNo;
        this.settlementSessionCnt = settlementSessionCnt;
        this.paymentTiming = paymentTiming;
        this.tuitionFee = tuitionFee;
        this.sameDayCancelRecognized = false;
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.accountNumber = digits;
        this.accountLast4 = digits.substring(digits.length() - ACCOUNT_LAST_DIGITS);
        this.accountHolder = accountHolder;
    }
}
