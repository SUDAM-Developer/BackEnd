package com.sudam.global.seed;

import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.entity.InvoiceSnapshot;
import com.sudam.domain.invoice.enums.InvoiceKind;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.tutoring.entity.TutoringPolicy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

/**
 * 수입 화면(T-15) 카드 1장이 될 선불 과외. 회차 · 기록은 만들지 않고 사이클 2 청구 하나만 만든다.
 * 네 장을 합치면 요약이 1,200,000 / 400,000 / 800,000이 되도록 금액 · 상태를 맞췄다.
 *
 * @param studentName            학생 이름
 * @param gradeLevel             학년 (1~12 = 초1~고3)
 * @param settlementSessionCount 정산 회차
 * @param tuitionFee             수업료 (원)
 * @param paymentState           청구를 어떤 상태로 만들지
 * @param unpaidReminderEnabled  공유 설정의 미입금 알림
 * @param lessonDays             수업 요일
 * @param lessonStart            수업 시작 시각 (90분 수업)
 * @param textbookName           교재 1권 이름
 */
record DemoIncomeCard(
        String studentName,
        int gradeLevel,
        int settlementSessionCount,
        int tuitionFee,
        PaymentState paymentState,
        boolean unpaidReminderEnabled,
        List<DayOfWeek> lessonDays,
        LocalTime lessonStart,
        String textbookName
) {

    static final int PARTIAL_PAID_AMOUNT = 120_000;

    // 카드 표시 순서(확인 대기 → 입금 대기 → 일부 입금 → 입금 완료)와 같은 순서로 둔다. 시간은 김서아(18:00~19:30)와 겹치지 않게 골랐다.
    static final List<DemoIncomeCard> ALL = List.of(
            new DemoIncomeCard("박지후", 8, 8, 360_000, PaymentState.CONFIRM_REQUESTED, false,
                    List.of(TUESDAY, THURSDAY), LocalTime.of(16, 0), "천일문 기본"),
            new DemoIncomeCard("윤서아", 9, 8, 360_000, PaymentState.PENDING, true,
                    List.of(MONDAY, WEDNESDAY), LocalTime.of(16, 0), "리딩튜터 중급"),
            new DemoIncomeCard("이도윤", 11, 4, 200_000, PaymentState.PARTIAL, false,
                    List.of(SATURDAY), LocalTime.of(10, 0), "Grammar Zone 기본"),
            new DemoIncomeCard("김하린", 7, 8, 280_000, PaymentState.PAID, false,
                    List.of(TUESDAY, FRIDAY), LocalTime.of(20, 0), "능률 VOCA 중학")
    );

    /***
     * 함수 기능: 사이클 2를 청구하는 선불 '다음 N회차' 청구를 만들고 카드에 맞는 상태로 바꾼다.
     *          정산 서비스의 선불 청구와 같은 모양으로 만든다: 리포트 없음(시드는 리포트를 만들지 않음),
     *          스냅샷의 인정 회차 · 시작일은 방금 정산한 사이클 1 기준 (회차를 다 채워 정산했으므로 인정 회차 = 정산 회차), 휴강 없음.
     * @param policy           이 과외의 정책 (사이클 1부터 적용 = 사이클 2에도 적용)
     * @param billedCycleId    청구 대상 사이클(사이클 2) ID
     * @param settledCycleStart 정산한 사이클 1의 시작일
     * @param parent           확인 요청을 누른 학부모 (CONFIRM_REQUESTED일 때만 쓴다)
     * @param now              실행 시각
     * @return 저장 전 Invoice
     */
    Invoice issueInvoice(TutoringPolicy policy, Long billedCycleId, LocalDate settledCycleStart, Participant parent,
                         LocalDateTime now) {
        Invoice invoice = Invoice.issue(policy, billedCycleId, null, InvoiceKind.NEXT, policy.getTuitionFee(),
                InvoiceSnapshot.of(policy, settlementSessionCount, settledCycleStart, List.of()));
        switch (paymentState) {
            case CONFIRM_REQUESTED -> invoice.requestConfirm(parent, now.minusHours(2));
            case PENDING -> {
                // 그대로 둔다.
            }
            case PARTIAL -> invoice.recordPayment(PARTIAL_PAID_AMOUNT, now.minusDays(1));
            case PAID -> invoice.recordPayment(null, now.minusDays(1));
        }
        return invoice;
    }

    enum PaymentState {
        CONFIRM_REQUESTED, // 학부모가 [입금했어요]를 누름 (실행 2시간 전)
        PENDING, // 입금 대기
        PARTIAL, // 120,000원만 입금 (실행 하루 전)
        PAID // 전액 입금 (실행 하루 전)
    }
}
