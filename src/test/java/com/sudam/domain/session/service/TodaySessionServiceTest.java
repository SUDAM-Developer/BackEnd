package com.sudam.domain.session.service;

import com.sudam.domain.session.dto.response.TodaySessionsResponse;
import com.sudam.domain.session.dto.response.TodaySessionsResponse.LessonSubject;
import com.sudam.domain.session.dto.response.TodaySessionsResponse.RegularSchedule;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import com.sudam.domain.tutoring.enums.TutoringStatus;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import com.sudam.domain.tutoring.repository.TutoringScheduleRepository;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("TodaySessionService 오늘 수업 목록 조회")
class TodaySessionServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final Long TEACHER_ID = 1L;
    private static final Long SEOA_ID = 7L;
    private static final Long HAJUN_ID = 5L;
    private static final Long JIWOO_ID = 9L;

    @Mock private TutoringRepository tutoringRepository;
    @Mock private TutoringScheduleRepository tutoringScheduleRepository;
    @Mock private LessonCalendar lessonCalendar;

    private TodaySessionService todaySessionService;
    private TeacherProfile teacher;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(SEOUL).toInstant(), SEOUL);
        todaySessionService = new TodaySessionService(tutoringRepository, tutoringScheduleRepository,
                lessonCalendar, clock);
        teacher = TeacherProfile.create(User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app"),
                "한서윤", List.of(Subject.ENGLISH), null);
    }

    @Test
    @DisplayName("오늘 수업이 없으면 빈 목록이고 사이클 · 정규 일정은 읽지 않는다")
    void 오늘_수업_없음() {
        // given
        List<Tutoring> tutorings = List.of(tutoring(SEOA_ID, "윤서아", List.of(Subject.ENGLISH), null));
        givenTutorings(tutorings);
        given(lessonCalendar.lessonsOf(tutorings, TODAY, TODAY)).willReturn(List.of());

        // when
        TodaySessionsResponse response = todaySessionService.today(TEACHER_ID);

        // then
        assertThat(response.date()).isEqualTo(TODAY);
        assertThat(response.sessions()).isEmpty();
        then(lessonCalendar).should(never()).timelines(anyCollection(), any());
        then(tutoringScheduleRepository).should(never()).findEffectiveIn(anyCollection(), any(), any());
    }

    @Test
    @DisplayName("저장 전 회차는 sessionId가 null, 저장된 회차는 저장 행 값으로 카드를 만들고 계산기 순서를 유지한다")
    void 카드_필드와_순서() {
        // given
        Tutoring seoa = tutoring(SEOA_ID, "윤서아", List.of(Subject.ENGLISH), null);
        Tutoring hajun = tutoring(HAJUN_ID, "김하준", List.of(Subject.MATH), null);
        List<Tutoring> tutorings = List.of(hajun, seoa);
        givenTutorings(tutorings);
        LessonSlot completed = stored(HAJUN_ID, 1042L, 28L, LocalTime.of(15, 0), SessionStatus.COMPLETED, true);
        LessonSlot planned = planned(SEOA_ID, 31L, LocalTime.of(18, 0));
        given(lessonCalendar.lessonsOf(tutorings, TODAY, TODAY)).willReturn(List.of(completed, planned));
        givenCycles(tutorings, Map.of(
                completed, new SlotCycle(1, 3, 3, 4, false, false),
                planned, new SlotCycle(1, 5, 4, 5, false, true)));
        givenRegularSchedules(List.of());

        // when
        List<TodaySessionsResponse.Lesson> lessons = todaySessionService.today(TEACHER_ID).sessions();

        // then
        // 생성자 인자가 같은 타입끼리 뒤바뀌어도 컴파일되므로 필드마다 서로 다른 값으로 확인한다.
        assertThat(lessons).hasSize(2);
        TodaySessionsResponse.Lesson first = lessons.get(0);
        assertThat(first.sessionId()).isEqualTo(1042L);
        assertThat(first.scheduleId()).isEqualTo(28L);
        assertThat(first.originalDate()).isEqualTo(TODAY);
        assertThat(first.tutoringId()).isEqualTo(HAJUN_ID);
        assertThat(first.studentName()).isEqualTo("김하준");
        assertThat(first.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(first.startTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(first.endTime()).isEqualTo(LocalTime.of(16, 30));
        assertThat(first.isTimeChanged()).isTrue();
        assertThat(first.cycle()).isEqualTo(new TodaySessionsResponse.Cycle(3, 3, 4));

        TodaySessionsResponse.Lesson second = lessons.get(1);
        assertThat(second.sessionId()).isNull();
        assertThat(second.scheduleId()).isEqualTo(31L);
        assertThat(second.tutoringId()).isEqualTo(SEOA_ID);
        assertThat(second.studentName()).isEqualTo("윤서아");
        assertThat(second.gradeLevel()).isEqualTo(10);
        assertThat(second.gradeOther()).isNull();
        assertThat(second.sessionType()).isEqualTo(SessionType.REGULAR);
        assertThat(second.status()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(second.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(second.isTimeChanged()).isFalse();
        assertThat(second.cycle()).isEqualTo(new TodaySessionsResponse.Cycle(5, 4, 5));
    }

    @Test
    @DisplayName("미인정 휴강 회차는 상태가 CANCELED이고 sessionNo가 null이다")
    void 미인정_휴강() {
        // given
        Tutoring seoa = tutoring(SEOA_ID, "윤서아", List.of(Subject.ENGLISH), null);
        List<Tutoring> tutorings = List.of(seoa);
        givenTutorings(tutorings);
        LessonSlot canceled = stored(SEOA_ID, 1050L, 31L, LocalTime.of(18, 0), SessionStatus.CANCELED, false);
        given(lessonCalendar.lessonsOf(tutorings, TODAY, TODAY)).willReturn(List.of(canceled));
        givenCycles(tutorings, Map.of(canceled, new SlotCycle(1, null, 4, 5, false, false)));
        givenRegularSchedules(List.of());

        // when
        TodaySessionsResponse.Lesson lesson = todaySessionService.today(TEACHER_ID).sessions().get(0);

        // then
        assertThat(lesson.status()).isEqualTo(SessionStatus.CANCELED);
        assertThat(lesson.cycle()).isEqualTo(new TodaySessionsResponse.Cycle(null, 4, 5));
    }

    @Test
    @DisplayName("과목은 과목 순서대로 모두 담고, 기타는 직접 입력한 과목명(없으면 '기타')으로 내려준다")
    void 여러_과목() {
        // given
        Tutoring seoa = tutoring(SEOA_ID, "윤서아", List.of(Subject.OTHER, Subject.MATH, Subject.ENGLISH), "논술");
        Tutoring hajun = tutoring(HAJUN_ID, "김하준", List.of(Subject.OTHER), null);
        List<Tutoring> tutorings = List.of(hajun, seoa);
        givenTutorings(tutorings);
        LessonSlot hajunLesson = planned(HAJUN_ID, 28L, LocalTime.of(15, 0));
        LessonSlot seoaLesson = planned(SEOA_ID, 31L, LocalTime.of(18, 0));
        given(lessonCalendar.lessonsOf(tutorings, TODAY, TODAY)).willReturn(List.of(hajunLesson, seoaLesson));
        givenCycles(tutorings, Map.of(
                hajunLesson, new SlotCycle(1, 1, 0, 4, false, true),
                seoaLesson, new SlotCycle(1, 1, 0, 4, false, true)));
        givenRegularSchedules(List.of());

        // when
        List<TodaySessionsResponse.Lesson> lessons = todaySessionService.today(TEACHER_ID).sessions();

        // then
        assertThat(lessons.get(0).subjects()).containsExactly(new LessonSubject(Subject.OTHER, "기타"));
        assertThat(lessons.get(1).subjects()).containsExactly(
                new LessonSubject(Subject.ENGLISH, "영어"),
                new LessonSubject(Subject.MATH, "수학"),
                new LessonSubject(Subject.OTHER, "논술"));
    }

    @Test
    @DisplayName("정규 일정은 과외별로 요일 → 시작 시각순으로 담고 다른 과외의 일정은 섞지 않는다")
    void 정규_일정() {
        // given
        Tutoring seoa = tutoring(SEOA_ID, "윤서아", List.of(Subject.ENGLISH), null);
        Tutoring hajun = tutoring(HAJUN_ID, "김하준", List.of(Subject.MATH), null);
        List<Tutoring> tutorings = List.of(hajun, seoa);
        givenTutorings(tutorings);
        LessonSlot hajunLesson = planned(HAJUN_ID, 28L, LocalTime.of(15, 0));
        LessonSlot seoaLesson = planned(SEOA_ID, 33L, LocalTime.of(18, 0));
        given(lessonCalendar.lessonsOf(tutorings, TODAY, TODAY)).willReturn(List.of(hajunLesson, seoaLesson));
        givenCycles(tutorings, Map.of(
                hajunLesson, new SlotCycle(1, 1, 0, 4, false, true),
                seoaLesson, new SlotCycle(1, 1, 0, 4, false, true)));
        // 쿼리 결과 순서는 보장되지 않으므로 일부러 섞어서 돌려준다.
        givenRegularSchedules(List.of(
                schedule(seoa, 5, LocalTime.of(18, 0)),
                schedule(hajun, 4, LocalTime.of(15, 0)),
                schedule(seoa, 1, LocalTime.of(18, 0)),
                schedule(seoa, 4, LocalTime.of(20, 0)),
                schedule(seoa, 4, LocalTime.of(18, 0))));

        // when
        List<TodaySessionsResponse.Lesson> lessons = todaySessionService.today(TEACHER_ID).sessions();

        // then
        assertThat(lessons.get(0).regularSchedules()).containsExactly(
                new RegularSchedule(4, LocalTime.of(15, 0), LocalTime.of(16, 30)));
        assertThat(lessons.get(1).regularSchedules()).containsExactly(
                new RegularSchedule(1, LocalTime.of(18, 0), LocalTime.of(19, 30)),
                new RegularSchedule(4, LocalTime.of(18, 0), LocalTime.of(19, 30)),
                new RegularSchedule(4, LocalTime.of(20, 0), LocalTime.of(21, 30)),
                new RegularSchedule(5, LocalTime.of(18, 0), LocalTime.of(19, 30)));
    }

    @Test
    @DisplayName("한 과외가 오늘 두 번 수업하면 카드가 두 장이고, 오늘 수업이 없는 과외는 사이클 · 정규 일정 조회에서 뺀다")
    void 조회_대상_과외() {
        // given
        Tutoring seoa = tutoring(SEOA_ID, "윤서아", List.of(Subject.ENGLISH), null);
        Tutoring hajun = tutoring(HAJUN_ID, "김하준", List.of(Subject.MATH), null);
        Tutoring jiwoo = tutoring(JIWOO_ID, "박지우", List.of(Subject.KOREAN), null);
        List<Tutoring> tutorings = List.of(hajun, seoa, jiwoo);
        givenTutorings(tutorings);
        LessonSlot regular = planned(HAJUN_ID, 28L, LocalTime.of(15, 0));
        LessonSlot makeup = new LessonSlot(HAJUN_ID, 1060L, null, null, TODAY, LocalTime.of(20, 0),
                LocalTime.of(21, 30), SessionType.MAKEUP, SessionStatus.SCHEDULED, null, null, false);
        LessonSlot seoaLesson = planned(SEOA_ID, 31L, LocalTime.of(18, 0));
        given(lessonCalendar.lessonsOf(tutorings, TODAY, TODAY)).willReturn(List.of(regular, seoaLesson, makeup));
        List<Tutoring> lessonTutorings = List.of(hajun, seoa);
        givenCycles(lessonTutorings, Map.of(
                regular, new SlotCycle(1, 3, 2, 4, false, true),
                makeup, new SlotCycle(1, 4, 2, 4, false, true),
                seoaLesson, new SlotCycle(1, 5, 4, 5, false, true)));
        given(tutoringScheduleRepository.findEffectiveIn(Set.of(HAJUN_ID, SEOA_ID), TODAY, TODAY))
                .willReturn(List.of());

        // when
        List<TodaySessionsResponse.Lesson> lessons = todaySessionService.today(TEACHER_ID).sessions();

        // then
        assertThat(lessons).extracting(TodaySessionsResponse.Lesson::tutoringId)
                .containsExactly(HAJUN_ID, SEOA_ID, HAJUN_ID);
        assertThat(lessons.get(2).sessionType()).isEqualTo(SessionType.MAKEUP);
        assertThat(lessons.get(2).scheduleId()).isNull();
        assertThat(lessons.get(2).cycle().sessionNo()).isEqualTo(4);
        then(lessonCalendar).should().timelines(lessonTutorings, TODAY);
    }

    private void givenTutorings(List<Tutoring> tutorings) {
        given(tutoringRepository.findAllByTeacherIdAndStatusAndDeletedAtIsNullOrderById(TEACHER_ID,
                TutoringStatus.ACTIVE)).willReturn(tutorings);
    }

    // 과외마다 타임라인 목을 만들어 회차별 사이클 정보를 돌려준다.
    private void givenCycles(List<Tutoring> tutorings, Map<LessonSlot, SlotCycle> cycles) {
        Map<Long, CycleTimeline> timelines = new java.util.HashMap<>();
        for (Tutoring tutoring : tutorings) {
            timelines.put(tutoring.getId(), mock(CycleTimeline.class));
        }
        cycles.forEach((slot, cycle) -> given(timelines.get(slot.tutoringId()).cycleOf(slot)).willReturn(cycle));
        given(lessonCalendar.timelines(tutorings, TODAY)).willReturn(Map.copyOf(timelines));
    }

    private void givenRegularSchedules(List<TutoringSchedule> schedules) {
        given(tutoringScheduleRepository.findEffectiveIn(anyCollection(), any(), any())).willReturn(schedules);
    }

    private Tutoring tutoring(Long id, String studentName, List<Subject> subjects, String subjectOther) {
        Tutoring tutoring = Tutoring.builder().teacher(teacher).subjects(subjects).subjectOther(subjectOther)
                .studentName(studentName).gradeLevel(10).gradeSchoolYear(2026).build();
        ReflectionTestUtils.setField(tutoring, "id", id);
        return tutoring;
    }

    // 1시간 30분짜리 반복 일정.
    private static TutoringSchedule schedule(Tutoring tutoring, int dayOfWeek, LocalTime startTime) {
        return TutoringSchedule.builder().tutoring(tutoring).dayOfWeek(dayOfWeek)
                .startTime(startTime).endTime(startTime.plusMinutes(90))
                .effectiveFrom(LocalDate.of(2026, 9, 1)).build();
    }

    // 계산된 예정 회차 (저장 전 정규 회차).
    private static LessonSlot planned(Long tutoringId, Long scheduleId, LocalTime startTime) {
        return new LessonSlot(tutoringId, null, scheduleId, TODAY, TODAY, startTime, startTime.plusMinutes(90),
                SessionType.REGULAR, SessionStatus.SCHEDULED, null, null, false);
    }

    // 저장된 정규 회차 (기록 · 휴강).
    private static LessonSlot stored(Long tutoringId, Long sessionId, Long scheduleId, LocalTime startTime,
                                     SessionStatus status, boolean timeChanged) {
        return new LessonSlot(tutoringId, sessionId, scheduleId, TODAY, TODAY, startTime, startTime.plusMinutes(90),
                SessionType.REGULAR, status, status == SessionStatus.COMPLETED, 100L, timeChanged);
    }
}
