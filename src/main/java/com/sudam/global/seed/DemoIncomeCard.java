package com.sudam.global.seed;

import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.entity.InvoiceSnapshot;
import com.sudam.domain.invoice.enums.InvoiceKind;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.record.enums.HomeworkResult;
import com.sudam.domain.record.enums.Understanding;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.global.seed.DemoLessonPlan.LessonNote;

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
 * 수입 화면(T-15) 카드 1장이 될 선불 과외. 사이클 1을 정산하고 사이클 2를 청구한 상태로 만든다.
 * 네 장을 합치면 요약이 1,200,000 / 400,000 / 800,000이 되도록 금액 · 상태를 맞췄다.
 * 사이클 1에 회차 · 기록 · 진도를 깔고 정산 리포트를 만들어 청구에 연결한다 (T-13 · T-14).
 * 실제 정산은 사이클을 닫을 때 항상 리포트를 만들므로, 시드도 모든 카드에 리포트를 둔다.
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
 * @param notes                  사이클 1 회차 기록 (정산 회차만큼, 오래된 순)
 * @param aiDraftComment         사이클 1 리포트의 AI 초안 코멘트 (UT에서는 AI 생성 대신 시드가 넣는다)
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
        String textbookName,
        List<LessonNote> notes,
        String aiDraftComment
) {

    static final int PARTIAL_PAID_AMOUNT = 120_000;

    // 회차를 다 채워 정산했다고 두므로 (인정 회차 = 정산 회차) 기록 수가 정산 회차와 같아야 한다.
    DemoIncomeCard {
        if (notes.size() != settlementSessionCount) {
            throw new IllegalArgumentException(
                    "기록 수가 정산 회차와 다릅니다. student=" + studentName + ", notes=" + notes.size());
        }
    }

    // 학생마다 이해도 분포를 다르게 둔다 (T-14에서 박지후 상 · 윤서아 중 · 이도윤 하로 보이도록).
    private static final List<LessonNote> JIHU_NOTES = List.of(
            new LessonNote(Understanding.HIGH, null, "Unit 1~2 구문 복습", "주어 · 동사 찾기를 빠르게 함"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 3~4 예문 해석", "to부정사 용법 구분이 정확함"),
            new LessonNote(Understanding.MEDIUM, HomeworkResult.DONE, "Unit 5~6 오답 정리", "분사구문에서 의미상 주어를 헷갈려함"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 7~8 예습", "지난 시간 오답을 스스로 고쳐 옴"),
            new LessonNote(Understanding.HIGH, HomeworkResult.PARTIAL, "Unit 9~10 문장 쓰기", "관계대명사 문장 만들기를 잘함"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 11~12 해석 연습", "긴 문장도 끊어 읽기가 안정적임"),
            new LessonNote(Understanding.MEDIUM, HomeworkResult.DONE, "Unit 13~14 복습", "가정법 시제 바꾸기에서 실수가 있음"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 15~16 예습", "가정법 실수가 줄고 설명을 바로 이해함")
    );
    private static final List<LessonNote> SEOA_NOTES = List.of(
            new LessonNote(Understanding.MEDIUM, null, "Unit 1~2 단어 정리", "지문은 이해하지만 단어를 자주 물어봄"),
            new LessonNote(Understanding.MEDIUM, HomeworkResult.DONE, "Unit 3~4 요약 쓰기", "주제문 찾기는 맞히고 세부 내용에서 틀림"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 5~6 지문 복습", "관심 있는 과학 지문이라 집중이 좋았음"),
            new LessonNote(Understanding.MEDIUM, HomeworkResult.PARTIAL, "Unit 7~8 단어 암기", "단어 시험 18/25"),
            new LessonNote(Understanding.LOW, HomeworkResult.NOT_DONE, "Unit 9~10 다시 읽기", "숙제를 못 해 와서 지문 이해가 늦었음"),
            new LessonNote(Understanding.MEDIUM, HomeworkResult.DONE, "Unit 11~12 요약 쓰기", "요약 문장이 짧아졌지만 핵심은 잡음"),
            new LessonNote(Understanding.MEDIUM, HomeworkResult.PARTIAL, "Unit 13~14 오답 정리", "추론 문제 근거 찾기를 연습 중"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 15~16 예습", "단어 시험 23/25로 올라감")
    );
    private static final List<LessonNote> DOYUN_NOTES = List.of(
            new LessonNote(Understanding.LOW, null, "Unit 1~2 개념 다시 보기", "시제 개념이 많이 비어 있어 기초부터 다시 설명"),
            new LessonNote(Understanding.LOW, HomeworkResult.PARTIAL, "Unit 3~4 문제 풀기", "완료시제와 과거시제를 계속 헷갈려함"),
            new LessonNote(Understanding.MEDIUM, HomeworkResult.DONE, "Unit 5~6 오답 정리", "조동사는 비교적 잘 따라옴"),
            new LessonNote(Understanding.LOW, HomeworkResult.NOT_DONE, "Unit 7~8 다시 풀기", "학원 시험 기간이라 숙제를 못 함. 수동태 다시 설명")
    );
    private static final List<LessonNote> HARIN_NOTES = List.of(
            new LessonNote(Understanding.MEDIUM, null, "Unit 1~2 단어 암기", "첫 수업. 발음은 좋고 철자를 자주 틀림"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 3~4 단어 암기", "단어 시험 27/30"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 5~6 예문 만들기", "새 단어로 문장을 스스로 만듦"),
            new LessonNote(Understanding.MEDIUM, HomeworkResult.PARTIAL, "Unit 7~8 단어 암기", "비슷한 뜻 단어를 헷갈려함"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 9~10 누적 복습", "단어 시험 29/30"),
            new LessonNote(Understanding.MEDIUM, HomeworkResult.DONE, "Unit 11~12 예문 만들기", "접두사 뜻 정리를 어려워함"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 13~14 단어 암기", "접두사로 처음 보는 단어 뜻을 추측함"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 15~16 누적 복습", "누적 시험 56/60")
    );

    // 기록 메모를 바탕으로 쓴 AI 초안. 선생님이 확인 · 수정하는 흐름(T-14)을 보여주려고 확인 전 상태로 넣는다.
    private static final String JIHU_COMMENT = "이번 달 지후는 천일문 기본 Unit 1부터 16까지 진도를 나갔습니다. "
            + "긴 문장도 끊어 읽기가 안정적이고, to부정사 · 관계대명사 문장을 정확하게 해석하고 직접 만들어 냅니다. "
            + "분사구문과 가정법 시제에서 실수가 있었지만, 지난 시간 오답을 스스로 고쳐 오면서 마지막 수업에는 실수가 눈에 띄게 줄었습니다. "
            + "다음 달에는 실전 지문에 구문 분석을 적용하는 연습을 이어 가겠습니다.";
    private static final String SEOA_COMMENT = "이번 달 서아는 리딩튜터 중급 Unit 1부터 16까지 지문을 읽고 요약하는 연습을 했습니다. "
            + "주제문은 잘 찾지만 세부 내용과 추론 문제에서 근거를 찾는 데 아직 시간이 걸립니다. "
            + "숙제를 못 해 온 주에는 지문 이해가 늦었는데, 단어 시험이 18/25에서 23/25로 오르면서 독해도 함께 좋아지고 있습니다. "
            + "다음 달에는 단어 복습을 꾸준히 이어 가면서 추론 문제 근거 찾기를 집중해서 연습하겠습니다.";
    private static final String DOYUN_COMMENT = "이번 달 도윤이는 Grammar Zone 기본 Unit 1부터 8까지 시제 · 조동사 · 수동태를 다뤘습니다. "
            + "시제 개념이 많이 비어 있어 기초부터 다시 설명했고, 완료시제와 과거시제를 아직 헷갈려합니다. "
            + "조동사는 비교적 잘 따라왔습니다. 학원 시험 기간에는 숙제를 하기 어려웠는데, "
            + "다음 달에는 숙제 양을 조절해 매주 시제 문제를 꾸준히 풀 수 있게 하겠습니다.";
    private static final String HARIN_COMMENT = "이번 달 하린이는 능률 VOCA 중학 Unit 1부터 16까지 단어를 익혔습니다. "
            + "첫 수업 때는 철자를 자주 틀렸지만, 단어 시험이 27/30에서 29/30으로 오르고 누적 시험도 56/60을 받았습니다. "
            + "접두사 뜻 정리를 어려워했는데, 마지막 수업에서는 접두사로 처음 보는 단어의 뜻을 스스로 추측해 냈습니다. "
            + "다음 달에는 비슷한 뜻 단어를 구분하는 연습을 더하겠습니다.";

    // 카드 표시 순서(확인 대기 → 입금 대기 → 일부 입금 → 입금 완료)와 같은 순서로 둔다. 시간은 김서아(18:00~19:30)와 겹치지 않게 골랐다.
    static final List<DemoIncomeCard> ALL = List.of(
            new DemoIncomeCard("박지후", 8, 8, 360_000, PaymentState.CONFIRM_REQUESTED, false,
                    List.of(TUESDAY, THURSDAY), LocalTime.of(16, 0), "천일문 기본", JIHU_NOTES,
                    JIHU_COMMENT),
            new DemoIncomeCard("윤서아", 9, 8, 360_000, PaymentState.PENDING, true,
                    List.of(MONDAY, WEDNESDAY), LocalTime.of(16, 0), "리딩튜터 중급", SEOA_NOTES,
                    SEOA_COMMENT),
            new DemoIncomeCard("이도윤", 11, 4, 200_000, PaymentState.PARTIAL, false,
                    List.of(SATURDAY), LocalTime.of(10, 0), "Grammar Zone 기본", DOYUN_NOTES,
                    DOYUN_COMMENT),
            new DemoIncomeCard("김하린", 7, 8, 280_000, PaymentState.PAID, false,
                    List.of(TUESDAY, FRIDAY), LocalTime.of(20, 0), "능률 VOCA 중학", HARIN_NOTES,
                    HARIN_COMMENT)
    );

    /***
     * 함수 기능: 사이클 1 회차 날짜를 고른다. 사이클 시작일부터 수업 요일을 앞으로 세어 기록 수만큼.
     *          사이클 1은 4주(28일)이므로 주 2회 8개 · 주 1회 4개가 정산일 전에 모두 들어간다.
     * @param cycleStartDate 사이클 1 시작일
     * @return 오래된 순 회차 날짜 (기록 수만큼)
     */
    List<LocalDate> firstCycleLessonDates(LocalDate cycleStartDate) {
        return DemoLessonPlan.lessonDatesFrom(cycleStartDate, lessonDays, notes.size());
    }

    /***
     * 함수 기능: 사이클 2를 청구하는 선불 '다음 N회차' 청구를 만들고 카드에 맞는 상태로 바꾼다.
     *          정산 서비스의 선불 청구와 같은 모양으로 만든다: 방금 정산한 사이클 1의 리포트를 연결하고,
     *          스냅샷의 인정 회차 · 시작일은 사이클 1 기준 (회차를 다 채워 정산했으므로 인정 회차 = 정산 회차), 휴강 없음.
     * @param policy           이 과외의 정책 (사이클 1부터 적용 = 사이클 2에도 적용)
     * @param billedCycleId    청구 대상 사이클(사이클 2) ID
     * @param reportId         사이클 1 정산 리포트 ID
     * @param settledCycleStart 정산한 사이클 1의 시작일
     * @param parent           확인 요청을 누른 학부모 (CONFIRM_REQUESTED일 때만 쓴다)
     * @param now              실행 시각
     * @return 저장 전 Invoice
     */
    Invoice issueInvoice(TutoringPolicy policy, Long billedCycleId, Long reportId, LocalDate settledCycleStart,
                         Participant parent, LocalDateTime now) {
        Invoice invoice = Invoice.issue(policy, billedCycleId, reportId, InvoiceKind.NEXT, policy.getTuitionFee(),
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
