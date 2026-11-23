package com.sudam.domain.schedule.service;

import com.sudam.domain.record.repository.SessionRecordRepository;
import com.sudam.domain.schedule.dto.response.DailyScheduleResponse;
import com.sudam.domain.schedule.dto.response.MonthlyScheduleResponse;
import com.sudam.domain.schedule.enums.LessonAction;
import com.sudam.domain.schedule.exception.ScheduleException;
import com.sudam.domain.schedule.exception.code.ScheduleErrorCode;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;
import com.sudam.domain.session.service.CycleTimeline;
import com.sudam.domain.session.service.LessonCalendar;
import com.sudam.domain.session.service.LessonSlot;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.enums.TutoringStatus;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 일정 탭 조회 (5.1.1). 회차는 모두 공통 예정 회차 계산기({@link LessonCalendar})로 만들고 여기서는 화면 모양으로 조립만 한다.
 * 과외 수와 상관없이 쿼리 수가 일정하다 — 계산기가 과외 id IN으로 묶어 읽고, 기록 여부도 회차 id IN 한 번으로 확인한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    // daily '다가오는 수업'을 찾는 범위와 건수 (5.1.1 처리 규칙 5).
    private static final int UPCOMING_WEEKS = 8;
    private static final int UPCOMING_LIMIT = 3;

    private final TutoringRepository tutoringRepository;
    private final LessonCalendar lessonCalendar;
    private final SessionRecordRepository sessionRecordRepository;
    private final Clock clock;

    /***
     * 함수 기능: 한 달 달력의 수업 점 · 학생 범례를 만든다. 종료 · 삭제한 과외는 달력과 범례에서 뺀다.
     * @param teacherId 선생님 ID
     * @param yearMonth 요청한 달
     * @return 수업이 있는 날만 날짜순으로 담은 달력
     */
    public MonthlyScheduleResponse monthly(Long teacherId, YearMonth yearMonth) {
        List<Tutoring> tutorings = activeTutoringsOf(teacherId);
        List<LessonSlot> slots = lessonCalendar.lessonsOf(tutorings, yearMonth.atDay(1), yearMonth.atEndOfMonth());

        // 계산기 결과가 이미 날짜 → 시작 시각순이라 날짜로 묶기만 하면 점 순서가 유지된다.
        Map<LocalDate, List<MonthlyScheduleResponse.Lesson>> lessonsByDate = slots.stream()
                .collect(Collectors.groupingBy(LessonSlot::date, TreeMap::new, Collectors.mapping(
                        slot -> new MonthlyScheduleResponse.Lesson(slot.tutoringId(),
                                slot.status() == SessionStatus.CANCELED, slot.sessionType() == SessionType.MAKEUP),
                        Collectors.toList())));
        List<MonthlyScheduleResponse.Day> days = lessonsByDate.entrySet().stream()
                .map(entry -> new MonthlyScheduleResponse.Day(entry.getKey(), List.copyOf(entry.getValue()),
                        entry.getValue().size(), List.of()))
                .toList();
        List<MonthlyScheduleResponse.Student> students = tutorings.stream()
                .map(tutoring -> new MonthlyScheduleResponse.Student(tutoring.getId(), tutoring.getStudentName()))
                .toList();
        return new MonthlyScheduleResponse(yearMonth, LocalDate.now(clock), students, days);
    }

    /***
     * 함수 기능: 그날 수업 목록과, 오늘이면 지금 이후 다가오는 수업 3건을 만든다.
     * @param teacherId 선생님 ID
     * @param date 고른 날짜. null이면 오늘
     * @param tutoringId 한 과외만 볼 때. null이면 수업 중 과외 전체
     * @return 그날 일정 · 수업 · 다가오는 수업
     * @throws ScheduleException tutoringId가 내 수업 중 과외가 아닌 경우 (SCHEDULE404_1)
     */
    public DailyScheduleResponse daily(Long teacherId, LocalDate date, Long tutoringId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalDate target = date == null ? today : date;
        boolean isToday = target.equals(today);
        List<Tutoring> tutorings = targetTutorings(teacherId, tutoringId);

        // 오늘이면 그날 회차와 다가오는 수업을 한 번의 계산으로 함께 만든다.
        LocalDate until = isToday ? today.plusWeeks(UPCOMING_WEEKS) : target;
        List<LessonSlot> slots = lessonCalendar.lessonsOf(tutorings, target, until);
        List<LessonSlot> daySlots = slots.stream().filter(slot -> slot.date().equals(target)).toList();
        List<LessonSlot> upcomingSlots = isToday
                ? slots.stream().filter(slot -> isAfter(slot, now)).limit(UPCOMING_LIMIT).toList()
                : List.of();

        // 지난 날짜면 예측할 미래 회차가 없으니 오늘까지만 타임라인을 만든다.
        Map<Long, CycleTimeline> timelines = lessonCalendar.timelines(tutorings, until.isBefore(today) ? today : until);
        Set<Long> recorded = recordedSessionIds(Stream.concat(daySlots.stream(), upcomingSlots.stream()));
        Map<Long, Tutoring> tutoringById = tutorings.stream()
                .collect(Collectors.toMap(Tutoring::getId, Function.identity()));
        Function<LessonSlot, DailyScheduleResponse.Lesson> toLesson = slot -> toLesson(slot,
                tutoringById.get(slot.tutoringId()), timelines.get(slot.tutoringId()), recorded, today);

        return new DailyScheduleResponse(target, isToday, List.of(),
                daySlots.stream().map(toLesson).toList(),
                upcomingSlots.stream().map(toLesson).toList());
    }

    /***
     * 함수 기능: 회차 카드 오른쪽 버튼을 정한다 (5.1.1 처리 규칙 4).
     *          지난 회차에는 [조율]이 없고, 정산이 끝난 날짜의 미기록 회차는 계산기가 만들지 않는다.
     * @param slot 회차
     * @param hasRecord 기록이 있는지
     * @param today 오늘
     * @return 기록됨 · 휴강 · 보강은 NONE, 오늘 · 지난 미기록 정규는 RECORD, 앞으로의 정규 예정은 ADJUST
     */
    static LessonAction actionOf(LessonSlot slot, boolean hasRecord, LocalDate today) {
        if (hasRecord || slot.status() != SessionStatus.SCHEDULED || slot.sessionType() != SessionType.REGULAR) {
            return LessonAction.NONE;
        }
        return slot.date().isAfter(today) ? LessonAction.ADJUST : LessonAction.RECORD;
    }

    private DailyScheduleResponse.Lesson toLesson(LessonSlot slot, Tutoring tutoring, CycleTimeline timeline,
                                                  Set<Long> recorded, LocalDate today) {
        boolean hasRecord = slot.isStored() && recorded.contains(slot.sessionId());
        // cancelReason · pendingTimeChange · makeup은 휴강 사유 컬럼 · 시간 변경(PR-10) · 보강 기능이 생기면 채운다.
        return new DailyScheduleResponse.Lesson(slot.sessionId(), slot.scheduleId(), slot.originalDate(),
                slot.date(), slot.startTime(), slot.endTime(), tutoring.getId(), tutoring.getStudentName(),
                tutoring.subjectNames(), slot.sessionType(), slot.status(), null, hasRecord, slot.timeChanged(),
                false, null, DailyScheduleResponse.Cycle.from(timeline.cycleOf(slot)),
                actionOf(slot, hasRecord, today));
    }

    private List<Tutoring> targetTutorings(Long teacherId, Long tutoringId) {
        List<Tutoring> tutorings = activeTutoringsOf(teacherId);
        if (tutoringId == null) {
            return tutorings;
        }
        return List.of(tutorings.stream()
                .filter(tutoring -> tutoring.getId().equals(tutoringId))
                .findFirst()
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.TUTORING_NOT_FOUND)));
    }

    private List<Tutoring> activeTutoringsOf(Long teacherId) {
        return tutoringRepository.findAllByTeacherIdAndStatusAndDeletedAtIsNullOrderById(teacherId,
                TutoringStatus.ACTIVE);
    }

    private Set<Long> recordedSessionIds(Stream<LessonSlot> slots) {
        Set<Long> storedIds = slots.filter(LessonSlot::isStored).map(LessonSlot::sessionId)
                .collect(Collectors.toSet());
        return storedIds.isEmpty() ? Set.of() : sessionRecordRepository.findRecordedSessionIds(storedIds);
    }

    // 지금 시각 이후에 시작하는 회차. 시각이 없는 회차는 날짜만으로 판단한다.
    private static boolean isAfter(LessonSlot slot, LocalDateTime now) {
        if (!slot.date().equals(now.toLocalDate())) {
            return slot.date().isAfter(now.toLocalDate());
        }
        return slot.startTime() != null && slot.startTime().isAfter(now.toLocalTime());
    }
}
