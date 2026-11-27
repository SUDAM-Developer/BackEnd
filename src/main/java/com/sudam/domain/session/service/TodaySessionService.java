package com.sudam.domain.session.service;

import com.sudam.domain.session.dto.response.TodaySessionsResponse;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import com.sudam.domain.tutoring.enums.TutoringStatus;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import com.sudam.domain.tutoring.repository.TutoringScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 홈 화면 오늘 수업 목록.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodaySessionService {

    private static final Comparator<TutoringSchedule> REGULAR_SCHEDULE_ORDER = Comparator
            .comparing(TutoringSchedule::getDayOfWeek)
            .thenComparing(TutoringSchedule::getStartTime);

    private final TutoringRepository tutoringRepository;
    private final TutoringScheduleRepository tutoringScheduleRepository;
    private final LessonCalendar lessonCalendar;
    private final Clock clock;

    /***
     * 함수 기능: 선생님의 오늘 수업 목록을 만든다. 종료 · 삭제한 과외는 뺀다. 휴강 · 기록 완료 회차도 포함한다.
     * @param teacherId 선생님 ID
     * @return 오늘 날짜와 오늘 수업(시작 시각순)
     */
    public TodaySessionsResponse today(Long teacherId) {
        LocalDate today = LocalDate.now(clock);

        // 선생님의 수업 중 과외(ACTIVE, 삭제 제외)를 읽는다.
        List<Tutoring> tutorings = tutoringRepository.findAllByTeacherIdAndStatusAndDeletedAtIsNullOrderById(
                teacherId, TutoringStatus.ACTIVE);

        // 선생님의 수업 중인 과외에서 오늘 날짜에 해당하는 회차 목록
        List<LessonSlot> slots = lessonCalendar.lessonsOf(tutorings, today, today);
        if (slots.isEmpty()) {
            return new TodaySessionsResponse(today, List.of());
        }

        // 사이클 · 정규 일정은 오늘 수업이 있는 과외만 읽는다.
        Set<Long> lessonTutoringIds = slots.stream().map(LessonSlot::tutoringId).collect(Collectors.toSet());
        List<Tutoring> lessonTutorings = tutorings.stream()
                .filter(tutoring -> lessonTutoringIds.contains(tutoring.getId()))
                .toList();
        Map<Long, Tutoring> tutoringById = lessonTutorings.stream()
                .collect(Collectors.toMap(Tutoring::getId, tutoring -> tutoring));
        Map<Long, CycleTimeline> timelines = lessonCalendar.timelines(lessonTutorings, today);
        Map<Long, List<TodaySessionsResponse.RegularSchedule>> regularSchedules =
                regularSchedulesOf(lessonTutoringIds, today);

        List<TodaySessionsResponse.Lesson> lessons = slots.stream()
                .map(slot -> TodaySessionsResponse.Lesson.of(slot, tutoringById.get(slot.tutoringId()),
                        timelines.get(slot.tutoringId()).cycleOf(slot),
                        regularSchedules.getOrDefault(slot.tutoringId(), List.of()), today))
                .toList();
        return new TodaySessionsResponse(today, lessons);
    }

    // 오늘 적용 중인 반복 일정을 과외별로 요일 → 시작 시각순으로 묶는다.
    private Map<Long, List<TodaySessionsResponse.RegularSchedule>> regularSchedulesOf(Set<Long> tutoringIds,
                                                                                      LocalDate today) {
        return tutoringScheduleRepository.findEffectiveIn(tutoringIds, today, today).stream()
                .sorted(REGULAR_SCHEDULE_ORDER)
                .collect(Collectors.groupingBy(schedule -> schedule.getTutoring().getId(), Collectors.mapping(
                        TodaySessionsResponse.RegularSchedule::from, Collectors.toUnmodifiableList())));
    }
}
