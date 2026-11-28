package com.sudam.domain.schedule.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.record.repository.SessionRecordRepository;
import com.sudam.domain.schedule.dto.response.DailyScheduleResponse;
import com.sudam.domain.schedule.dto.response.MonthlyScheduleResponse;
import com.sudam.domain.schedule.enums.LessonAction;
import com.sudam.domain.schedule.exception.ScheduleException;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;
import com.sudam.domain.session.repository.RecognizedSession;
import com.sudam.domain.session.service.CycleTimeline;
import com.sudam.domain.session.service.LessonCalendar;
import com.sudam.domain.session.service.LessonSlot;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.tutoring.enums.TutoringStatus;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import com.sudam.domain.tutoring.repository.TutoringScheduleRepository;
import com.sudam.domain.user.enums.Subject;
import com.sudam.global.exception.ProjectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleService 일정 탭 daily · monthly")
class ScheduleServiceTest {

    private static final Long TEACHER_ID = 3L;
    private static final Long SEOA_ID = 31L;
    private static final Long DOYUN_ID = 35L;
    private static final Long SEOA_SCHEDULE_ID = 7L;
    private static final Long DOYUN_SCHEDULE_ID = 9L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-11-11은 수요일이다. 지금은 17:00.
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private static final LocalTime NOW = LocalTime.of(17, 0);

    @Mock private TutoringRepository tutoringRepository;
    @Mock private LessonCalendar lessonCalendar;
    @Mock private SessionRecordRepository sessionRecordRepository;
    @Mock private TutoringScheduleRepository tutoringScheduleRepository;

    private ScheduleService scheduleService;
    private Tutoring seoa;
    private Tutoring doyun;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(NOW).atZone(SEOUL).toInstant(), SEOUL);
        scheduleService = new ScheduleService(tutoringRepository, lessonCalendar, sessionRecordRepository,
                tutoringScheduleRepository, clock);
        seoa = tutoring(SEOA_ID, "김서아", List.of(Subject.MATH));
        doyun = tutoring(DOYUN_ID, "이도윤", List.of(Subject.MATH, Subject.ENGLISH));
    }

    @Nested
    @DisplayName("daily")
    class Daily {

        @Test
        @DisplayName("오늘 미기록 정규 회차는 [기록하기]이고 학생 이름 · 과목 · 예측 사이클 번호를 담는다")
        void 오늘_미기록_회차는_기록하기() {
            // given
            LessonSlot today = planned(SEOA_ID, SEOA_SCHEDULE_ID, TODAY, 18, 0);
            givenActiveTutorings(seoa);
            given(lessonCalendar.lessonsOf(List.of(seoa), TODAY, TODAY.plusWeeks(8))).willReturn(List.of(today));
            givenTimelines(timeline(seoa, 8, List.of(today)));

            // when
            DailyScheduleResponse response = scheduleService.daily(TEACHER_ID, null, null);

            // then
            assertThat(response.date()).isEqualTo(TODAY);
            assertThat(response.isToday()).isTrue();
            assertThat(response.events()).isEmpty();
            assertThat(response.sessions()).singleElement().satisfies(lesson -> {
                assertThat(lesson.studentName()).isEqualTo("김서아");
                assertThat(lesson.subjectName()).isEqualTo("수학");
                assertThat(lesson.action()).isEqualTo(LessonAction.RECORD);
                assertThat(lesson.hasRecord()).isFalse();
                assertThat(lesson.cycle().noInCycle()).isEqualTo(1);
                assertThat(lesson.cycle().settlementSessionCount()).isEqualTo(8);
                assertThat(lesson.cycle().isPredicted()).isTrue();
            });
        }

        @Test
        @DisplayName("앞으로의 정규 예정 회차는 [조율]이고 다가오는 수업은 비어 있다")
        void 앞으로의_정규_회차는_조율() {
            // given
            LocalDate friday = TODAY.plusDays(2);
            LessonSlot future = planned(DOYUN_ID, DOYUN_SCHEDULE_ID, friday, 20, 30);
            givenActiveTutorings(doyun);
            given(lessonCalendar.lessonsOf(List.of(doyun), friday, friday)).willReturn(List.of(future));
            given(lessonCalendar.timelines(List.of(doyun), friday))
                    .willReturn(Map.of(DOYUN_ID, timeline(doyun, 4, List.of(future))));

            // when
            DailyScheduleResponse response = scheduleService.daily(TEACHER_ID, friday, null);

            // then
            assertThat(response.isToday()).isFalse();
            assertThat(response.upcoming()).isEmpty();
            assertThat(response.sessions()).singleElement().satisfies(lesson -> {
                assertThat(lesson.subjectName()).isEqualTo("영어 · 수학");
                assertThat(lesson.action()).isEqualTo(LessonAction.ADJUST);
            });
        }

        @Test
        @DisplayName("지난 날은 미기록이면 [기록하기], 기록됨 · 휴강 · 보강이면 버튼이 없다")
        void 지난_날_회차별_버튼() {
            // given
            LocalDate monday = TODAY.minusDays(2);
            LessonSlot unrecorded = planned(SEOA_ID, SEOA_SCHEDULE_ID, monday, 16, 0);
            LessonSlot recorded = stored(101L, DOYUN_ID, DOYUN_SCHEDULE_ID, monday, 17, 0, SessionType.REGULAR,
                    SessionStatus.COMPLETED, true);
            LessonSlot canceled = stored(102L, SEOA_ID, SEOA_SCHEDULE_ID, monday, 18, 0, SessionType.REGULAR,
                    SessionStatus.CANCELED, false);
            LessonSlot makeup = stored(103L, DOYUN_ID, null, monday, 20, 0, SessionType.MAKEUP,
                    SessionStatus.SCHEDULED, null);
            givenActiveTutorings(seoa, doyun);
            given(lessonCalendar.lessonsOf(List.of(seoa, doyun), monday, monday))
                    .willReturn(List.of(unrecorded, recorded, canceled, makeup));
            given(lessonCalendar.timelines(List.of(seoa, doyun), TODAY)).willReturn(Map.of(
                    SEOA_ID, timeline(seoa, 8, List.of()),
                    DOYUN_ID, timeline(doyun, 4, List.of(), new RecognizedSession(DOYUN_ID, 101L, cycleIdOf(doyun),
                            monday))));
            given(sessionRecordRepository.findRecordedSessionIds(anyCollection())).willReturn(Set.of(101L));

            // when
            DailyScheduleResponse response = scheduleService.daily(TEACHER_ID, monday, null);

            // then
            assertThat(response.sessions())
                    .extracting(DailyScheduleResponse.Lesson::hasRecord, DailyScheduleResponse.Lesson::action)
                    .containsExactly(
                            tuple(false, LessonAction.RECORD),
                            tuple(true, LessonAction.NONE),
                            tuple(false, LessonAction.NONE),
                            tuple(false, LessonAction.NONE));
            assertThat(response.sessions().get(1).cycle().noInCycle()).isEqualTo(1);
            assertThat(response.sessions().get(1).cycle().isPredicted()).isFalse();
        }

        @Test
        @DisplayName("오늘이면 지금 이후 수업 3건을 다가오는 수업으로 담고, 이미 시작한 오늘 수업은 뺀다")
        void 오늘이면_다가오는_수업_3건() {
            // given
            LessonSlot started = planned(SEOA_ID, SEOA_SCHEDULE_ID, TODAY, 16, 0);
            LessonSlot tonight = planned(DOYUN_ID, DOYUN_SCHEDULE_ID, TODAY, 20, 30);
            LessonSlot canceledFriday = stored(201L, SEOA_ID, SEOA_SCHEDULE_ID, TODAY.plusDays(2), 16, 0,
                    SessionType.REGULAR, SessionStatus.CANCELED, false);
            LessonSlot nextMonday = planned(SEOA_ID, SEOA_SCHEDULE_ID, TODAY.plusDays(5), 16, 0);
            LessonSlot nextWednesday = planned(SEOA_ID, SEOA_SCHEDULE_ID, TODAY.plusDays(7), 16, 0);
            List<LessonSlot> slots = List.of(started, tonight, canceledFriday, nextMonday, nextWednesday);
            givenActiveTutorings(seoa, doyun);
            given(lessonCalendar.lessonsOf(List.of(seoa, doyun), TODAY, TODAY.plusWeeks(8))).willReturn(slots);
            given(lessonCalendar.timelines(List.of(seoa, doyun), TODAY.plusWeeks(8))).willReturn(Map.of(
                    SEOA_ID, timeline(seoa, 8, List.of(started, nextMonday, nextWednesday)),
                    DOYUN_ID, timeline(doyun, 4, List.of(tonight))));
            given(sessionRecordRepository.findRecordedSessionIds(anyCollection())).willReturn(Set.of());

            // when
            DailyScheduleResponse response = scheduleService.daily(TEACHER_ID, null, null);

            // then
            assertThat(response.sessions()).extracting(DailyScheduleResponse.Lesson::startTime)
                    .containsExactly(LocalTime.of(16, 0), LocalTime.of(20, 30));
            assertThat(response.upcoming())
                    .extracting(DailyScheduleResponse.Lesson::date, DailyScheduleResponse.Lesson::status)
                    .containsExactly(
                            tuple(TODAY, SessionStatus.SCHEDULED),
                            tuple(TODAY.plusDays(2), SessionStatus.CANCELED),
                            tuple(TODAY.plusDays(5), SessionStatus.SCHEDULED));
        }

        @Test
        @DisplayName("tutoringId를 주면 그 과외만 계산기에 넘긴다")
        void 과외_하나만_조회() {
            // given
            givenActiveTutorings(seoa, doyun);
            given(lessonCalendar.lessonsOf(List.of(doyun), TODAY, TODAY.plusWeeks(8))).willReturn(List.of());
            given(lessonCalendar.timelines(List.of(doyun), TODAY.plusWeeks(8)))
                    .willReturn(Map.of(DOYUN_ID, timeline(doyun, 4, List.of())));

            // when
            DailyScheduleResponse response = scheduleService.daily(TEACHER_ID, null, DOYUN_ID);

            // then
            assertThat(response.sessions()).isEmpty();
            assertThat(response.upcoming()).isEmpty();
            then(sessionRecordRepository).should(never()).findRecordedSessionIds(anyCollection());
            then(tutoringScheduleRepository).should(never()).findEffectiveIn(anyCollection(), any(), any());
        }

        @Test
        @DisplayName("정규 일정은 요일 → 시작 시각순으로 담는다")
        void 정규_일정은_요일_시각순() {
            // given
            LessonSlot today = planned(SEOA_ID, SEOA_SCHEDULE_ID, TODAY, 18, 0);
            givenActiveTutorings(seoa);
            given(lessonCalendar.lessonsOf(List.of(seoa), TODAY, TODAY.plusWeeks(8))).willReturn(List.of(today));
            givenTimelines(timeline(seoa, 8, List.of(today)));
            given(tutoringScheduleRepository.findEffectiveIn(Set.of(SEOA_ID), TODAY, TODAY.plusWeeks(8)))
                    .willReturn(List.of(
                            schedule(seoa, 5, 18, LocalDate.of(2026, 9, 1), null),
                            schedule(seoa, 3, 18, LocalDate.of(2026, 9, 1), null),
                            schedule(seoa, 3, 16, LocalDate.of(2026, 9, 1), null)));

            // when
            DailyScheduleResponse response = scheduleService.daily(TEACHER_ID, null, null);

            // then
            assertThat(response.sessions().getFirst().regularSchedules())
                    .extracting(DailyScheduleResponse.RegularSchedule::dayOfWeek,
                            DailyScheduleResponse.RegularSchedule::startTime)
                    .containsExactly(
                            tuple(3, LocalTime.of(16, 0)),
                            tuple(3, LocalTime.of(18, 0)),
                            tuple(5, LocalTime.of(18, 0)));
        }

        @Test
        @DisplayName("정규 일정은 카드 날짜에 적용 중인 것만 담아, 다음 주에 요일이 바뀌면 다가오는 수업 카드는 바뀐 일정을 받는다")
        void 정규_일정은_카드_날짜_기준() {
            // given: 수요일 일정은 다음 화요일(11/17)까지, 다음 수요일(11/18)부터는 목요일 일정
            LocalDate oldRuleEnd = TODAY.plusDays(6);
            LessonSlot tonight = planned(SEOA_ID, SEOA_SCHEDULE_ID, TODAY, 18, 0);
            LessonSlot nextThursday = planned(SEOA_ID, 8L, TODAY.plusDays(8), 18, 0);
            givenActiveTutorings(seoa);
            given(lessonCalendar.lessonsOf(List.of(seoa), TODAY, TODAY.plusWeeks(8)))
                    .willReturn(List.of(tonight, nextThursday));
            givenTimelines(timeline(seoa, 8, List.of(tonight, nextThursday)));
            given(tutoringScheduleRepository.findEffectiveIn(Set.of(SEOA_ID), TODAY, TODAY.plusWeeks(8)))
                    .willReturn(List.of(
                            schedule(seoa, 3, 18, LocalDate.of(2026, 9, 1), oldRuleEnd),
                            schedule(seoa, 4, 18, oldRuleEnd.plusDays(1), null)));

            // when
            DailyScheduleResponse response = scheduleService.daily(TEACHER_ID, null, null);

            // then
            assertThat(response.sessions().getFirst().regularSchedules())
                    .extracting(DailyScheduleResponse.RegularSchedule::dayOfWeek)
                    .containsExactly(3);
            assertThat(response.upcoming())
                    .extracting(DailyScheduleResponse.Lesson::date)
                    .containsExactly(TODAY, TODAY.plusDays(8));
            assertThat(response.upcoming().get(1).regularSchedules())
                    .extracting(DailyScheduleResponse.RegularSchedule::dayOfWeek)
                    .containsExactly(4);
        }

        @Test
        @DisplayName("학년은 카드 날짜의 학년도로 계산한다 (2026학년도 8학년 → 2027-03 카드는 9학년)")
        void 학년은_카드_날짜_기준() {
            // given
            LocalDate nextSchoolYear = LocalDate.of(2027, 3, 3);
            LessonSlot march = planned(SEOA_ID, SEOA_SCHEDULE_ID, nextSchoolYear, 18, 0);
            givenActiveTutorings(seoa);
            given(lessonCalendar.lessonsOf(List.of(seoa), nextSchoolYear, nextSchoolYear)).willReturn(List.of(march));
            given(lessonCalendar.timelines(List.of(seoa), nextSchoolYear))
                    .willReturn(Map.of(SEOA_ID, timeline(seoa, 8, List.of(march))));

            // when
            DailyScheduleResponse response = scheduleService.daily(TEACHER_ID, nextSchoolYear, null);

            // then
            assertThat(response.sessions()).singleElement().satisfies(lesson -> {
                assertThat(lesson.gradeLevel()).isEqualTo(9);
                assertThat(lesson.gradeOther()).isNull();
            });
        }

        @Test
        @DisplayName("내 수업 중 과외가 아닌 tutoringId면 SCHEDULE404_1 예외가 발생한다")
        void 내_과외가_아니면_예외() {
            // given
            givenActiveTutorings(seoa);

            // when & then
            assertThatThrownBy(() -> scheduleService.daily(TEACHER_ID, null, 999L))
                    .isInstanceOf(ScheduleException.class)
                    .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                    .isEqualTo("SCHEDULE404_1");
            then(lessonCalendar).should(never()).lessonsOf(anyCollection(), any(), any());
        }
    }

    @Nested
    @DisplayName("monthly")
    class Monthly {

        @Test
        @DisplayName("수업이 있는 날만 날짜순으로 묶고 휴강 · 보강 표시와 수업 수를 담는다")
        void 날짜별로_수업_점을_묶는다() {
            // given
            YearMonth november = YearMonth.of(2026, 11);
            LessonSlot canceled = stored(301L, SEOA_ID, SEOA_SCHEDULE_ID, TODAY, 18, 0, SessionType.REGULAR,
                    SessionStatus.CANCELED, false);
            LessonSlot regular = planned(DOYUN_ID, DOYUN_SCHEDULE_ID, TODAY, 20, 30);
            LessonSlot makeup = stored(302L, SEOA_ID, null, TODAY.plusDays(2), 18, 0, SessionType.MAKEUP,
                    SessionStatus.SCHEDULED, null);
            givenActiveTutorings(seoa, doyun);
            given(lessonCalendar.lessonsOf(List.of(seoa, doyun), november.atDay(1), november.atEndOfMonth()))
                    .willReturn(List.of(canceled, regular, makeup));

            // when
            MonthlyScheduleResponse response = scheduleService.monthly(TEACHER_ID, november);

            // then
            assertThat(response.yearMonth()).isEqualTo(november);
            assertThat(response.today()).isEqualTo(TODAY);
            assertThat(response.students()).extracting(MonthlyScheduleResponse.Student::tutoringId,
                    MonthlyScheduleResponse.Student::studentName)
                    .containsExactly(tuple(SEOA_ID, "김서아"), tuple(DOYUN_ID, "이도윤"));
            assertThat(response.days()).extracting(MonthlyScheduleResponse.Day::date,
                    MonthlyScheduleResponse.Day::lessonCount)
                    .containsExactly(tuple(TODAY, 2), tuple(TODAY.plusDays(2), 1));
            assertThat(response.days().getFirst().lessons()).containsExactly(
                    new MonthlyScheduleResponse.Lesson(SEOA_ID, true, false),
                    new MonthlyScheduleResponse.Lesson(DOYUN_ID, false, false));
            assertThat(response.days().get(1).lessons()).containsExactly(
                    new MonthlyScheduleResponse.Lesson(SEOA_ID, false, true));
            assertThat(response.days()).allSatisfy(day -> assertThat(day.eventCategories()).isEmpty());
        }

        @Test
        @DisplayName("수업 중 과외가 없으면 범례와 날짜가 모두 비어 있다")
        void 과외가_없으면_빈_달력() {
            // given
            YearMonth november = YearMonth.of(2026, 11);
            givenActiveTutorings();
            given(lessonCalendar.lessonsOf(eq(List.of()), any(), any())).willReturn(List.of());

            // when
            MonthlyScheduleResponse response = scheduleService.monthly(TEACHER_ID, november);

            // then
            assertThat(response.students()).isEmpty();
            assertThat(response.days()).isEmpty();
        }
    }

    private void givenActiveTutorings(Tutoring... tutorings) {
        given(tutoringRepository.findAllByTeacherIdAndStatusAndDeletedAtIsNullOrderById(TEACHER_ID,
                TutoringStatus.ACTIVE)).willReturn(List.of(tutorings));
    }

    private void givenTimelines(CycleTimeline timeline) {
        given(lessonCalendar.timelines(List.of(seoa), TODAY.plusWeeks(8))).willReturn(Map.of(SEOA_ID, timeline));
    }

    // 사이클 1 진행 중, 정산 회차 settlementCount. upcoming은 예측에 쓸 오늘 이후 회차.
    private static CycleTimeline timeline(Tutoring tutoring, int settlementCount, List<LessonSlot> upcoming,
                                          RecognizedSession... recognized) {
        Cycle cycle = withId(Cycle.open(tutoring, 1, LocalDateTime.of(2026, 10, 1, 9, 0)), cycleIdOf(tutoring));
        TutoringPolicy policy = TutoringPolicy.builder().tutoring(tutoring).effectiveCycleNo(1)
                .settlementSessionCnt(settlementCount).paymentTiming(PaymentTiming.POSTPAID)
                .tuitionFee(300_000).bankName("국민").accountNumber("123-456-789012").accountHolder("한서윤")
                .build();
        return CycleTimeline.of(List.of(cycle), List.of(recognized), List.of(policy), new ArrayList<>(upcoming),
                TODAY);
    }

    private static Long cycleIdOf(Tutoring tutoring) {
        return tutoring.getId() * 10;
    }

    private static LessonSlot planned(Long tutoringId, Long scheduleId, LocalDate date, int hour, int minute) {
        LocalTime start = LocalTime.of(hour, minute);
        return new LessonSlot(tutoringId, null, scheduleId, date, date, start, start.plusMinutes(90),
                SessionType.REGULAR, SessionStatus.SCHEDULED, null, null, false);
    }

    private static LessonSlot stored(Long sessionId, Long tutoringId, Long scheduleId, LocalDate date, int hour,
                                     int minute, SessionType type, SessionStatus status, Boolean recognized) {
        LocalTime start = LocalTime.of(hour, minute);
        return new LessonSlot(tutoringId, sessionId, scheduleId, scheduleId == null ? null : date, date, start,
                start.plusMinutes(90), type, status, recognized, null, false);
    }

    // dayOfWeek 1(월)~7(일), hour 시 정각 시작 90분. effectiveTo는 빌더에 없어 리플렉션으로 넣는다.
    private static TutoringSchedule schedule(Tutoring tutoring, int dayOfWeek, int hour, LocalDate effectiveFrom,
                                             LocalDate effectiveTo) {
        LocalTime start = LocalTime.of(hour, 0);
        TutoringSchedule schedule = TutoringSchedule.builder().tutoring(tutoring).dayOfWeek(dayOfWeek)
                .startTime(start).endTime(start.plusMinutes(90)).effectiveFrom(effectiveFrom).build();
        ReflectionTestUtils.setField(schedule, "effectiveTo", effectiveTo);
        return schedule;
    }

    private static Tutoring tutoring(Long id, String studentName, Collection<Subject> subjects) {
        return withId(Tutoring.builder().subjects(subjects).studentName(studentName).gradeLevel(8)
                .gradeSchoolYear(2026).build(), id);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
