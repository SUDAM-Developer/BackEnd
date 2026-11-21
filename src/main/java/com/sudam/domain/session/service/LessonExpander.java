package com.sudam.domain.session.service;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 예정 회차 펼치기 규칙 (ERD 5. 수업 도메인 '예정 회차 계산'). DB 없이 테스트할 수 있게 순수 함수로 둔다.
 *
 * <ol>
 *   <li>반복 일정을 적용 기간 · 과외 기간 안에서 요일로 펼친다. 정산이 끝난 날짜까지는 만들지 않는다</li>
 *   <li>같은 (schedule_id, original_date)의 저장 행이 있으면 그 행으로 덮어쓴다</li>
 *   <li>저장 행은 실제 날짜(session_date)가 기간 안일 때만 내보낸다 (반복 일정에 없는 보강 · 추가 포함)</li>
 *   <li>정렬: 날짜 → 시작 시각(NULL 뒤) → 저장 회차 id → 일정 id</li>
 * </ol>
 */
public final class LessonExpander {

    /**
     * ERD 회차 정렬 기준(session_date → start_time NULL 뒤 → id). 계산 회차는 id가 없어 저장 회차 뒤에 두고 일정 id로 고정한다.
     */
    public static final Comparator<LessonSlot> ORDER = Comparator
            .comparing(LessonSlot::date)
            .thenComparing(LessonSlot::startTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(LessonSlot::sessionId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(LessonSlot::scheduleId, Comparator.nullsLast(Comparator.naturalOrder()));

    private LessonExpander() {
    }

    /***
     * 함수 기능: 과외들의 [from, to] 회차를 계산하고 저장 행으로 덮어써 정렬해 돌려준다.
     * @param spans 과외별 날짜 범위. 여기 없는 과외의 일정은 펼치지 않는다
     * @param rules 반복 일정 (기간과 겹치는 것만 넘겨도 된다)
     * @param stored 저장된 회차. session_date 또는 original_date가 기간 안인 행을 모두 넘긴다.
     *               날짜를 옮긴 기록(original_date만 기간 안)은 예정 회차를 지우는 데만 쓴다
     * @param from 시작 날짜 (포함)
     * @param to 끝 날짜 (포함)
     * @return 정렬된 회차 목록
     */
    public static List<LessonSlot> expand(Collection<TutoringSpan> spans, Collection<ScheduleRule> rules,
                                          Collection<LessonSlot> stored, LocalDate from, LocalDate to) {
        Map<Long, TutoringSpan> spanByTutoring = spans.stream()
                .collect(Collectors.toMap(TutoringSpan::tutoringId, Function.identity()));
        Map<Long, ScheduleRule> ruleById = rules.stream()
                .collect(Collectors.toMap(ScheduleRule::scheduleId, Function.identity()));
        Set<LessonSlot.Key> storedKeys = new HashSet<>();
        for (LessonSlot slot : stored) {
            if (slot.scheduleId() != null && slot.originalDate() != null) {
                storedKeys.add(new LessonSlot.Key(null, slot.scheduleId(), slot.originalDate()));
            }
        }

        List<LessonSlot> result = new ArrayList<>();
        for (ScheduleRule rule : rules) {
            TutoringSpan span = spanByTutoring.get(rule.tutoringId());
            if (span == null) {
                continue;
            }
            for (LocalDate date = firstOccurrence(rule, from); !date.isAfter(to); date = date.plusWeeks(1)) {
                if (occurs(span, rule, date) && !span.isLocked(date)
                        && !storedKeys.contains(new LessonSlot.Key(null, rule.scheduleId(), date))) {
                    result.add(LessonSlot.planned(rule, date));
                }
            }
        }
        for (LessonSlot slot : stored) {
            if (!slot.date().isBefore(from) && !slot.date().isAfter(to)) {
                result.add(fillTimes(slot, ruleById));
            }
        }
        result.sort(ORDER);
        return List.copyOf(result);
    }

    /***
     * 함수 기능: 날짜가 반복 일정의 수업 날짜인지 확인한다. 요일 · 일정 적용 기간 · 과외 기간(생성일 ~ 종료일)을 모두 본다.
     *          정산 잠금은 보지 않는다 — 기록 · 휴강 API가 '일정에 없는 날짜'와 '이미 정산된 날짜'를 다른 에러로 구분해야 해서다.
     * @param span 과외 날짜 범위
     * @param rule 반복 일정
     * @param date 확인할 날짜
     * @return 수업 날짜면 true
     */
    public static boolean occurs(TutoringSpan span, ScheduleRule rule, LocalDate date) {
        return rule.occursOn(date) && span.covers(date);
    }

    // from 이후(포함) 첫 번째 일정 요일.
    private static LocalDate firstOccurrence(ScheduleRule rule, LocalDate from) {
        return from.with(TemporalAdjusters.nextOrSame(rule.dayOfWeek()));
    }

    // 정규 기록 행은 시각을 저장하지 않으므로(시간을 바꾼 회차만 값이 있음) 반복 일정 시각을 보여준다.
    private static LessonSlot fillTimes(LessonSlot slot, Map<Long, ScheduleRule> ruleById) {
        ScheduleRule rule = slot.scheduleId() == null ? null : ruleById.get(slot.scheduleId());
        if (rule == null) {
            return slot;
        }
        return slot.withTimesIfAbsent(rule.startTime(), rule.endTime());
    }
}
