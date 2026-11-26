package com.sudam.domain.session.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.enums.CycleStatus;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.session.entity.Session;
import com.sudam.domain.session.repository.RecognizedSession;
import com.sudam.domain.session.repository.SessionRepository;
import com.sudam.domain.session.repository.TutoringLastSettledDate;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import com.sudam.domain.tutoring.enums.TutoringStatus;
import com.sudam.domain.tutoring.repository.TutoringPolicyRepository;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import com.sudam.domain.tutoring.repository.TutoringScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 공통 예정 회차 계산기 (ERD 5. 수업 도메인). 예정 회차는 저장하지 않고 반복 일정 + 저장된 회차로 매번 계산한다.
 * 홈 오늘 수업 · 일정 탭(daily · monthly) · 시간 바꾸기 · 기록 화면 · 휴강 · 보강 후보 검사 · 리포트 링크 '다음 수업' ·
 * 캘린더 피드가 모두 이 클래스를 쓴다. 규칙은 {@link LessonExpander}와 {@link CycleTimeline}에 있고 여기서는 조회와 조립만 한다.
 *
 * <p>읽기 전용 트랜잭션이지만 기록 · 휴강처럼 쓰기 트랜잭션 안에서 부르면 그 트랜잭션에 참여해 방금 저장한 행도 보인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LessonCalendar {

    private final TutoringRepository tutoringRepository;
    private final TutoringScheduleRepository tutoringScheduleRepository;
    private final SessionRepository sessionRepository;
    private final CycleRepository cycleRepository;
    private final TutoringPolicyRepository tutoringPolicyRepository;
    private final Clock clock;

    /***
     * 함수 기능: 과외들의 [from, to] 회차를 정렬해 돌려준다. 휴강 회차도 포함한다.
     *          마지막 정산 사이클의 마지막 인정 날짜까지는 계산 회차를 만들지 않는다 (저장 행은 그대로 나온다).
     * @param tutorings 대상 과외. 이름 · 학년 등을 쓰려고 호출하는 쪽이 이미 읽은 과외를 넘긴다 (상태 · 삭제 필터는 호출하는 쪽 몫)
     * @param from 시작 날짜 (포함)
     * @param to 끝 날짜 (포함)
     * @return 회차 목록. 정렬: 날짜 → 시작 시각(NULL 뒤) → 저장 회차 id → 일정 id
     */
    public List<LessonSlot> lessonsOf(Collection<Tutoring> tutorings, LocalDate from, LocalDate to) {
        if (tutorings.isEmpty()) {
            return List.of();
        }
        Set<Long> tutoringIds = idsOf(tutorings);
        Map<Long, LocalDate> lockedUntil = sessionRepository.findLastSettledDates(tutoringIds, CycleStatus.SETTLED)
                .stream()
                .collect(Collectors.toMap(TutoringLastSettledDate::tutoringId, TutoringLastSettledDate::lastDate));
        List<TutoringSpan> spans = tutorings.stream()
                .map(tutoring -> TutoringSpan.of(tutoring, lockedUntil.get(tutoring.getId())))
                .toList();
        return expand(tutoringIds, spans, from, to);
    }

    /***
     * 함수 기능: 선생님의 수업 중 과외(ACTIVE, 삭제 제외) 전체의 [from, to] 회차를 돌려준다.
     * @param teacherId 선생님 ID (= users.id)
     * @param from 시작 날짜 (포함)
     * @param to 끝 날짜 (포함)
     * @return 회차 목록
     */
    public List<LessonSlot> teacherLessons(Long teacherId, LocalDate from, LocalDate to) {
        return lessonsOf(activeTutoringsOf(teacherId), from, to);
    }

    /***
     * 함수 기능: 선생님의 그날 회차를 돌려준다. 시간 바꾸기 · 보강 후보의 겹침 검사(5.2.2 · 5.2.4)에 쓴다.
     *          휴강 회차도 들어 있으니 호출하는 쪽이 휴강과 자기 자신을 빼고 {@link LessonSlot#overlaps}로 검사한다.
     * @param teacherId 선생님 ID
     * @param date 날짜
     * @return 그날 회차 목록
     */
    public List<LessonSlot> findTeacherLessonsOn(Long teacherId, LocalDate date) {
        return teacherLessons(teacherId, date, date);
    }

    /***
     * 함수 기능: 회차 하나를 찾는다. 기록 화면 · 기록 · 휴강 · 시간 바꾸기가 요청의 대상 회차를 확인할 때 쓴다.
     *          반복 일정 회차는 일정 소속 · 요일 · 적용 기간 · 과외 기간(생성일 ~ 종료일)을 검사하고, 저장 행이 있으면 저장 행을 돌려준다.
     *          정산된 날짜인지는 보지 않는다 — 호출하는 쪽이 {@link CycleTimeline#lastSettledDate()}로 따로 검사한다.
     * @param tutoring 요청 경로의 과외 (소유권 검사가 끝난 것)
     * @param ref sessionId 또는 (scheduleId, originalDate)
     * @return 회차. 없는 회차 · 다른 과외의 회차 · 수업 날짜가 아닌 날짜면 빈 값
     */
    public Optional<LessonSlot> findLesson(Tutoring tutoring, LessonRef ref) {

        // 회차의 sessionId가 존재한다면 해당 session을 반환한다.
        // 다른 과외의 회차면 빈 값. 정규 행은 시각이 비어 있을 수 있어 반복 일정 시각으로 채운다.
        if (ref.isStored()) {
            return sessionRepository.findById(ref.sessionId())
                    .filter(session -> session.getTutoring().getId().equals(tutoring.getId()))
                    .map(this::storedWithScheduleTimes);
        }

        // 저장 전 반복 일정 회차: (scheduleId, originalDate)가 실제 수업 날짜인지 확인한다.
        // 과외 기간(생성일 ~ 종료일)만 보고 정산 잠금은 보지 않으므로 lockedUntil은 null로 둔다.
        TutoringSpan span = TutoringSpan.of(tutoring, null);
        return tutoringScheduleRepository.findById(ref.scheduleId())
                // 1. 일정이 이 과외의 것인지 확인한다. 아니면 빈 값.
                .filter(schedule -> schedule.getTutoring().getId().equals(tutoring.getId()))
                // 2. 엔티티를 계산용 값(요일 · 시각 · 적용 기간)으로 옮긴다.
                .map(ScheduleRule::from)
                // 3. 요일 · 일정 적용 기간 · 과외 기간이 모두 맞아야 수업 날짜다. 아니면 빈 값.
                .filter(rule -> LessonExpander.occurs(span, rule, ref.originalDate()))
                // 4. 같은 (일정, 원래 날짜)로 저장된 행(기록 · 휴강 · 시간 변경)이 있으면 그 행을, 없으면 계산된 예정 회차를 돌려준다.
                //    저장 행에 시각이 없으면(정규 기록) 반복 일정 시각으로 채운다.
                .map(rule -> sessionRepository.findByScheduleIdAndOriginalDate(rule.scheduleId(), ref.originalDate())
                        .map(LessonSlot::stored)
                        .map(slot -> slot.withTimesIfAbsent(rule.startTime(), rule.endTime()))
                        .orElseGet(() -> LessonSlot.planned(rule, ref.originalDate())));
    }

    /***
     * 함수 기능: 과외별 사이클 타임라인을 만든다. 오늘부터 until까지의 예정 회차로 다음 사이클을 예측한다.
     *          사이클 · 인정 회차 · 정책 · 일정 · 저장 행을 과외 수와 상관없이 IN 쿼리로 한 번씩만 읽는다.
     * @param tutorings 대상 과외
     * @param until 예측할 마지막 날짜 (포함). 화면에 보일 가장 늦은 예정 회차 날짜를 넘긴다 (예: daily '다가오는 수업' 8주)
     * @return 과외 ID → 타임라인
     */
    public Map<Long, CycleTimeline> timelines(Collection<Tutoring> tutorings, LocalDate until) {
        if (tutorings.isEmpty()) {
            return Map.of();
        }
        LocalDate today = LocalDate.now(clock);
        Set<Long> tutoringIds = idsOf(tutorings);
        Map<Long, List<Cycle>> cycles = cycleRepository.findAllByTutoringIdIn(tutoringIds).stream()
                .collect(Collectors.groupingBy(cycle -> cycle.getTutoring().getId()));
        Map<Long, List<RecognizedSession>> recognized = sessionRepository.findRecognizedSessions(tutoringIds).stream()
                .collect(Collectors.groupingBy(RecognizedSession::tutoringId));
        Map<Long, List<TutoringPolicy>> policies = tutoringPolicyRepository.findAllByTutoringIdIn(tutoringIds).stream()
                .collect(Collectors.groupingBy(policy -> policy.getTutoring().getId()));

        List<TutoringSpan> spans = tutorings.stream()
                .map(tutoring -> TutoringSpan.of(tutoring, CycleTimeline.lastSettledDate(
                        cycles.getOrDefault(tutoring.getId(), List.of()),
                        recognized.getOrDefault(tutoring.getId(), List.of())).orElse(null)))
                .toList();
        Map<Long, List<LessonSlot>> upcoming = until.isBefore(today) ? Map.of()
                : expand(tutoringIds, spans, today, until).stream()
                .collect(Collectors.groupingBy(LessonSlot::tutoringId));

        return tutoringIds.stream().collect(Collectors.toUnmodifiableMap(Function.identity(),
                tutoringId -> CycleTimeline.of(cycles.getOrDefault(tutoringId, List.of()),
                        recognized.getOrDefault(tutoringId, List.of()),
                        policies.getOrDefault(tutoringId, List.of()),
                        upcoming.getOrDefault(tutoringId, List.of()), today)));
    }

    private List<LessonSlot> expand(Set<Long> tutoringIds, List<TutoringSpan> spans, LocalDate from, LocalDate to) {
        List<ScheduleRule> rules = tutoringScheduleRepository.findEffectiveIn(tutoringIds, from, to).stream()
                .map(ScheduleRule::from)
                .toList();
        List<LessonSlot> stored = sessionRepository.findInPeriod(tutoringIds, from, to).stream()
                .map(LessonSlot::stored)
                .toList();
        return LessonExpander.expand(spans, rules, stored, from, to);
    }

    private List<Tutoring> activeTutoringsOf(Long teacherId) {
        return tutoringRepository.findAllByTeacherIdAndStatusAndDeletedAtIsNullOrderById(teacherId,
                TutoringStatus.ACTIVE);
    }

    // 정규 기록 행은 시각을 저장하지 않으므로 반복 일정 시각으로 채운다. 보강 · 추가 행은 행의 시각 그대로다.
    private LessonSlot storedWithScheduleTimes(Session session) {
        LessonSlot slot = LessonSlot.stored(session);
        TutoringSchedule schedule = session.getSchedule();
        return schedule == null ? slot : slot.withTimesIfAbsent(schedule.getStartTime(), schedule.getEndTime());
    }

    private static Set<Long> idsOf(Collection<Tutoring> tutorings) {
        return tutorings.stream().map(Tutoring::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
