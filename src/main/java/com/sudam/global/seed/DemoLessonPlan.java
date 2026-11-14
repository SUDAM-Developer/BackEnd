package com.sudam.global.seed;

import com.sudam.domain.record.enums.HomeworkResult;
import com.sudam.domain.record.enums.Understanding;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.WEDNESDAY;

/**
 * 시연용 과외(김서아, Hi-fi 1차 T-09 · T-14)의 일정 · 회차 · 기록 구성.
 * 저장소 없이 테스트할 수 있도록 날짜 계산과 고정 데이터만 둔다.
 */
final class DemoLessonPlan {

    static final String STUDENT_NAME = "김서아";
    static final int GRADE_LEVEL = 10; // 고1
    static final int SETTLEMENT_SESSION_COUNT = 8;
    static final int TUITION_FEE = 360_000; // 회당 45,000원
    static final String TEXTBOOK_NAME = "수능특강 영어";
    static final LocalTime LESSON_START = LocalTime.of(18, 0);
    static final LocalTime LESSON_END = LocalTime.of(19, 30);
    static final int UNITS_PER_SESSION = 2;

    private static final Set<DayOfWeek> MON_WED_FRI = EnumSet.of(MONDAY, WEDNESDAY, FRIDAY);

    // 오늘 전 회차 7개의 기록. 정산 회차 8에서 하나 모자라게 두어 오늘 기록 한 번으로 정산되게 한다.
    static final List<LessonNote> NOTES = List.of(
            new LessonNote(Understanding.HIGH, null, "Unit 1~2 단어 30개 암기", "첫 수업. 문장 구조를 빨리 파악함"),
            new LessonNote(Understanding.MEDIUM, HomeworkResult.DONE, "Unit 3~4 지문 복습", "빈칸 추론에서 근거 찾기를 어려워함"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 5~6 예습", "단어 시험 28/30"),
            new LessonNote(Understanding.MEDIUM, HomeworkResult.PARTIAL, "Unit 7~8 오답 정리", "긴 지문에서 집중력이 떨어짐"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 9~10 단어 암기", "주제 찾기 유형 정답률이 올라감"),
            new LessonNote(Understanding.LOW, HomeworkResult.NOT_DONE, "Unit 11~12 다시 풀기", "시험 기간이라 숙제를 못 함. 어법 개념 다시 설명"),
            new LessonNote(Understanding.HIGH, HomeworkResult.DONE, "Unit 13~14 예습", "어법 문제 실수가 줄었음")
    );

    static final int RECORDED_SESSION_COUNT = NOTES.size();

    private DemoLessonPlan() {
    }

    /***
     * 함수 기능: 수업 요일 3개를 고른다. 오늘이 항상 수업일이 되도록,
     *          오늘이 월 · 수 · 금 중 하나면 월 · 수 · 금, 아니면 오늘 요일과 그 뒤 2일 · 4일 요일.
     * @param today 실행일
     * @return 요일 순(월 → 일)으로 정렬한 수업 요일 3개
     */
    static List<DayOfWeek> lessonDays(LocalDate today) {
        DayOfWeek day = today.getDayOfWeek();
        if (MON_WED_FRI.contains(day)) {
            return List.of(MONDAY, WEDNESDAY, FRIDAY);
        }
        return Stream.of(day, day.plus(2), day.plus(4)).sorted().toList();
    }

    /***
     * 함수 기능: 오늘 전의 수업일을 거슬러 올라가며 count개 고른다. 오늘은 넣지 않는다 (= 기록 전).
     * @param today 실행일
     * @param days  수업 요일
     * @param count 고를 개수
     * @return 오래된 날짜부터 정렬한 수업일
     */
    static List<LocalDate> pastLessonDates(LocalDate today, Collection<DayOfWeek> days, int count) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = today.minusDays(1); dates.size() < count; date = date.minusDays(1)) {
            if (days.contains(date.getDayOfWeek())) {
                dates.add(date);
            }
        }
        Collections.reverse(dates);
        return List.copyOf(dates);
    }

    /***
     * 함수 기능: 학년도를 구한다. 한국 학년도는 3월에 시작하므로 1 · 2월은 전년도다.
     * @param today 실행일
     * @return 학년도
     */
    static int schoolYear(LocalDate today) {
        return today.getMonthValue() >= 3 ? today.getYear() : today.getYear() - 1;
    }

    // n번째(0부터) 회차의 진도 시작 단원. 회차마다 2단원씩 앞으로 간다 (1~2, 3~4, ...).
    static int progressStart(int index) {
        return index * UNITS_PER_SESSION + 1;
    }

    static int progressEnd(int index) {
        return (index + 1) * UNITS_PER_SESSION;
    }

    /**
     * 회차 기록 1개.
     *
     * @param understanding          이해도
     * @param previousHomeworkResult 지난 숙제 결과 (첫 회차는 NULL)
     * @param nextHomework           다음 숙제
     * @param observationMemo        관찰 메모
     */
    record LessonNote(Understanding understanding, HomeworkResult previousHomeworkResult,
                      String nextHomework, String observationMemo) {
    }
}
