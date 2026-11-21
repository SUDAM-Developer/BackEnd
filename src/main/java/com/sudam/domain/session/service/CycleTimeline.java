package com.sudam.domain.session.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.repository.RecognizedSession;
import com.sudam.domain.tutoring.entity.TutoringPolicies;
import com.sudam.domain.tutoring.entity.TutoringPolicy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 과외 1개의 사이클 상태와 회차별 사이클 번호 (ERD 5. '회차 · 사이클 계산 규칙'). 번호 · 기간 · 소속은 저장하지 않고 여기서 계산한다.
 *
 * <ul>
 *   <li>인정 회차: 소속 사이클 안에서 정렬 순서대로 번호 (규칙 2)</li>
 *   <li>미인정 휴강: 날짜로 소속 사이클을 정하고 번호 없음 (규칙 3 · 4)</li>
 *   <li>오늘 이후 예정 회차: 남은 회차(정산 회차 − 진행 중 인정 수)를 넘으면 다음 사이클. 다음 사이클 정산 회차는 정책 이력 (규칙 7)</li>
 *   <li>지난 미기록 회차: 지금 기록하면 될 번호(인정 수 + 1). 예측 순번은 차지하지 않는다</li>
 * </ul>
 * 진행 중 사이클 인정 수는 정산 회차보다 항상 작다 — 기록 저장이 정산 회차에 닿으면 같은 트랜잭션에서 정산하기 때문이다.
 */
public final class CycleTimeline {

    private final LocalDate today;
    private final List<TutoringPolicy> policies;
    private final int currentCycleNo;
    private final int currentRecognizedCount;
    // 정산한 사이클 번호 → 마지막 인정 날짜 (사이클 번호 오름차순). 미인정 휴강의 소속을 날짜로 정할 때 쓴다.
    private final LinkedHashMap<Integer, LocalDate> settledLastDates;
    private final Map<Integer, Integer> recognizedCountByCycleNo;
    private final Map<Long, SlotCycle> recognizedBySessionId;
    private final Map<LessonSlot.Key, SlotCycle> predicted;

    private CycleTimeline(LocalDate today, List<Cycle> cycles, List<RecognizedSession> recognized,
                          List<TutoringPolicy> policies, List<LessonSlot> upcoming) {
        this.today = today;
        this.policies = List.copyOf(policies);

        Map<Long, Integer> cycleNoById = cycles.stream().collect(Collectors.toMap(Cycle::getId, Cycle::getCycleNo));
        Map<Integer, List<RecognizedSession>> recognizedByCycleNo = recognized.stream()
                .collect(Collectors.groupingBy(session -> cycleNoById.get(session.cycleId()), LinkedHashMap::new,
                        Collectors.toList()));
        this.recognizedCountByCycleNo = recognizedByCycleNo.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size()));
        this.recognizedBySessionId = numberRecognized(recognizedByCycleNo);

        this.settledLastDates = new LinkedHashMap<>();
        cycles.stream()
                .filter(cycle -> !cycle.isInProgress())
                .map(Cycle::getCycleNo)
                .sorted()
                .forEach(cycleNo -> lastDateOf(recognizedByCycleNo.get(cycleNo))
                        .ifPresent(lastDate -> settledLastDates.put(cycleNo, lastDate)));

        // 진행 중 사이클이 없으면(수업 종료 등) 마지막 사이클 다음 번호를 진행 중으로 본다.
        this.currentCycleNo = cycles.stream().filter(Cycle::isInProgress).map(Cycle::getCycleNo).findFirst()
                .orElseGet(() -> cycles.stream().mapToInt(Cycle::getCycleNo).max().orElse(0) + 1);
        this.currentRecognizedCount = recognizedCountByCycleNo.getOrDefault(currentCycleNo, 0);
        this.predicted = predict(upcoming);
    }

    /***
     * 함수 기능: 과외 1개의 사이클 타임라인을 만든다.
     * @param cycles 과외의 사이클 전체
     * @param recognized 과외의 인정 회차 전체. ERD 정렬 기준(session_date → start_time NULL 뒤 → id) 순서여야 한다
     * @param policies 과외의 정책 이력 전체
     * @param upcoming 오늘부터 예측할 날짜까지의 회차 (LessonExpander 결과). 오늘 이후 SCHEDULED만 예측에 쓴다
     * @param today 오늘
     * @return 사이클 타임라인
     */
    public static CycleTimeline of(List<Cycle> cycles, List<RecognizedSession> recognized,
                                   List<TutoringPolicy> policies, List<LessonSlot> upcoming, LocalDate today) {
        return new CycleTimeline(today, cycles, recognized, policies, upcoming);
    }

    /***
     * 함수 기능: 마지막 정산 사이클의 마지막 인정 날짜를 구한다. 이 날짜까지는 기록 · 휴강이 막혀 있다 (4.1.1).
     * @param cycles 과외의 사이클
     * @param recognized 과외의 인정 회차
     * @return 정산한 사이클이 없으면 빈 값
     */
    public static Optional<LocalDate> lastSettledDate(Collection<Cycle> cycles,
                                                      Collection<RecognizedSession> recognized) {
        Set<Long> settledIds = cycles.stream().filter(cycle -> !cycle.isInProgress()).map(Cycle::getId)
                .collect(Collectors.toSet());
        return recognized.stream()
                .filter(session -> settledIds.contains(session.cycleId()))
                .map(RecognizedSession::date)
                .max(Comparator.naturalOrder());
    }

    /***
     * 함수 기능: 회차의 사이클 정보를 돌려준다.
     * @param slot LessonCalendar가 내보낸 회차
     * @return 사이클 번호 · 사이클 안 번호 · 예측 여부
     * @throws IllegalArgumentException 오늘 이후 예정 회차인데 타임라인을 만들 때 넘긴 예측 범위 밖인 경우
     */
    public SlotCycle cycleOf(LessonSlot slot) {
        if (slot.isStored() && recognizedBySessionId.containsKey(slot.sessionId())) {
            return recognizedBySessionId.get(slot.sessionId());
        }
        if (slot.status() != SessionStatus.SCHEDULED) {
            return byDate(slot.date());
        }
        if (slot.date().isBefore(today)) {
            return new SlotCycle(currentCycleNo, currentRecognizedCount + 1, currentRecognizedCount,
                    settlementSessionCount(currentCycleNo), false, true);
        }
        SlotCycle result = predicted.get(slot.key());
        if (result == null) {
            throw new IllegalArgumentException("예측 범위 밖의 회차입니다. date=" + slot.date());
        }
        return result;
    }

    public int currentCycleNo() {
        return currentCycleNo;
    }

    // 진행 중 사이클의 인정 회차 수. 홈 '오늘 기록하면 정산 (인정 수 + 1)/N회차'.
    public int recognizedCount() {
        return currentRecognizedCount;
    }

    /***
     * 함수 기능: 진행 중 사이클의 정산 회차를 돌려준다.
     * @return 정산 회차
     * @throws IllegalStateException 적용할 정책이 없는 경우. 과외를 만들 때 정책을 함께 저장하므로 데이터 이상이다
     */
    public int settlementSessionCount() {
        return settlementSessionCount(currentCycleNo);
    }

    public Optional<LocalDate> lastSettledDate() {
        return settledLastDates.values().stream().max(Comparator.naturalOrder());
    }

    private Map<Long, SlotCycle> numberRecognized(Map<Integer, List<RecognizedSession>> recognizedByCycleNo) {
        Map<Long, SlotCycle> result = new HashMap<>();
        recognizedByCycleNo.forEach((cycleNo, sessions) -> {
            int settlementCount = settlementSessionCount(cycleNo);
            for (int i = 0; i < sessions.size(); i++) {
                result.put(sessions.get(i).sessionId(),
                        new SlotCycle(cycleNo, i + 1, sessions.size(), settlementCount, false, false));
            }
        });
        return result;
    }

    // 오늘 이후 SCHEDULED 회차를 정렬 순서로 세어, 사이클의 정산 회차를 채우면 다음 사이클로 넘긴다.
    private Map<LessonSlot.Key, SlotCycle> predict(List<LessonSlot> upcoming) {
        List<LessonSlot> pending = new ArrayList<>(upcoming.stream()
                .filter(slot -> slot.status() == SessionStatus.SCHEDULED && !slot.date().isBefore(today))
                .toList());
        pending.sort(LessonExpander.ORDER);

        Map<LessonSlot.Key, SlotCycle> result = new HashMap<>();
        int cycleNo = currentCycleNo;
        int position = currentRecognizedCount;
        int recognizedInCycle = currentRecognizedCount;
        for (LessonSlot slot : pending) {
            if (position >= settlementSessionCount(cycleNo)) {
                cycleNo++;
                position = 0;
                recognizedInCycle = 0;
            }
            position++;
            boolean startsNewCycle = cycleNo > currentCycleNo && position == 1;
            result.put(slot.key(), new SlotCycle(cycleNo, position, recognizedInCycle,
                    settlementSessionCount(cycleNo), startsNewCycle, true));
        }
        return result;
    }

    private SlotCycle byDate(LocalDate date) {
        for (Map.Entry<Integer, LocalDate> settled : settledLastDates.entrySet()) {
            if (!date.isAfter(settled.getValue())) {
                int cycleNo = settled.getKey();
                return new SlotCycle(cycleNo, null, recognizedCountByCycleNo.getOrDefault(cycleNo, 0),
                        settlementSessionCount(cycleNo), false, false);
            }
        }
        return new SlotCycle(currentCycleNo, null, currentRecognizedCount, settlementSessionCount(currentCycleNo),
                false, false);
    }

    private int settlementSessionCount(int cycleNo) {
        return TutoringPolicies.applicableTo(policies, cycleNo)
                .map(TutoringPolicy::getSettlementSessionCnt)
                .orElseThrow(() -> new IllegalStateException("적용할 과외 정책이 없습니다. cycleNo=" + cycleNo));
    }

    private static Optional<LocalDate> lastDateOf(List<RecognizedSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(sessions.getLast().date());
    }
}
