package com.sudam.domain.record.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.enums.CycleStatus;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.record.dto.response.RecordFormResponse;
import com.sudam.domain.record.dto.response.RecordFormResponse.TextbookItem;
import com.sudam.domain.record.exception.RecordException;
import com.sudam.domain.record.repository.SessionProgressRepository;
import com.sudam.domain.record.repository.SessionRecordRepository;
import com.sudam.domain.record.repository.TextbookLastPosition;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;
import com.sudam.domain.session.service.CycleTimeline;
import com.sudam.domain.session.service.LessonCalendar;
import com.sudam.domain.session.service.LessonRef;
import com.sudam.domain.session.service.LessonSlot;
import com.sudam.domain.session.service.SlotCycle;
import com.sudam.domain.tutoring.entity.Textbook;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import com.sudam.domain.tutoring.enums.ProgressUnit;
import com.sudam.domain.tutoring.repository.TextbookRepository;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import com.sudam.domain.tutoring.repository.TutoringScheduleRepository;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import com.sudam.global.exception.ProjectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordFormService 회차 기록 화면 조회")
class RecordFormServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 30);
    private static final Long TEACHER_ID = 1L;
    private static final Long TUTORING_ID = 10L;
    private static final Long SCHEDULE_ID = 31L;

    @Mock private TutoringRepository tutoringRepository;
    @Mock private TutoringScheduleRepository tutoringScheduleRepository;
    @Mock private LessonCalendar lessonCalendar;
    @Mock private CycleRepository cycleRepository;
    @Mock private TextbookRepository textbookRepository;
    @Mock private SessionProgressRepository sessionProgressRepository;
    @Mock private SessionRecordRepository sessionRecordRepository;

    private RecordFormService recordFormService;
    private Tutoring tutoring;
    private TutoringSchedule schedule;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(SEOUL).toInstant(), SEOUL);
        recordFormService = new RecordFormService(tutoringRepository, tutoringScheduleRepository, lessonCalendar,
                cycleRepository, textbookRepository, sessionProgressRepository, sessionRecordRepository, clock);

        TeacherProfile teacher = withId(TeacherProfile.create(
                User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app"),
                "한서윤", List.of(Subject.ENGLISH), null), TEACHER_ID);
        tutoring = withId(tutoringOf(teacher), TUTORING_ID);
        schedule = withId(scheduleOf(tutoring), SCHEDULE_ID);
    }

    @Test
    @DisplayName("저장 전 회차면 회차 · 사이클 · 교재 · 지난 숙제를 각 필드에 담고 sessionId · record는 null이다")
    void 기록_화면_조회_성공() {
        // given
        givenOpenableLesson(WEDNESDAY);
        given(sessionProgressRepository.findPositionsBefore(TUTORING_ID, WEDNESDAY)).willReturn(List.of());
        given(textbookRepository.findAllByTutoringIdOrderById(TUTORING_ID)).willReturn(List.of());
        given(sessionRecordRepository.findPreviousHomework(TUTORING_ID, WEDNESDAY)).willReturn(Optional.of("6강 예습"));

        // when
        RecordFormResponse response = recordFormService.getRecordForm(TEACHER_ID, TUTORING_ID, SCHEDULE_ID, WEDNESDAY);

        // then
        // 빌더로 만든 응답이라 필드가 빠지거나 뒤바뀌어도 컴파일되므로, 모든 필드를 서로 다른 값으로 확인한다.
        assertThat(response.sessionId()).isNull();
        assertThat(response.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(response.originalDate()).isEqualTo(WEDNESDAY);
        assertThat(response.sessionDate()).isEqualTo(WEDNESDAY);
        assertThat(response.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(response.endTime()).isEqualTo(LocalTime.of(19, 30));
        assertThat(response.sessionType()).isEqualTo(SessionType.REGULAR);
        assertThat(response.studentName()).isEqualTo("윤서아");
        assertThat(response.noInCycle()).isEqualTo(8);
        assertThat(response.cycle()).isEqualTo(new RecordFormResponse.CycleSummary(2, 7, 9));
        assertThat(response.textbooks()).isEmpty();
        assertThat(response.previousHomework()).isEqualTo("6강 예습");
        assertThat(response.record()).isNull();
    }

    @Test
    @DisplayName("교재마다 최근 회차의 끝 진도를 쓰고, 앞선 기록이 없는 교재는 교재의 시작 진도를 쓴다")
    void 교재별_지난_진도() {
        // given
        givenOpenableLesson(WEDNESDAY);
        Textbook english = withId(textbookOf("수능특강 영어", ProgressUnit.CUSTOM, "강", 0), 11L);
        Textbook grammar = withId(textbookOf("Grammar Zone 기본", ProgressUnit.UNIT, null, 0), 12L);
        Textbook fresh = withId(textbookOf("새 교재", ProgressUnit.UNIT, null, 3), 13L);
        // 쿼리는 최근 회차부터 온다: 교재 11은 7(최근)과 5(이전)가 있으니 7이어야 한다.
        given(sessionProgressRepository.findPositionsBefore(TUTORING_ID, WEDNESDAY)).willReturn(List.of(
                new TextbookLastPosition(11L, 7),
                new TextbookLastPosition(12L, 17),
                new TextbookLastPosition(11L, 5)));
        given(textbookRepository.findAllByTutoringIdOrderById(TUTORING_ID)).willReturn(List.of(english, grammar, fresh));
        given(sessionRecordRepository.findPreviousHomework(TUTORING_ID, WEDNESDAY)).willReturn(Optional.empty());

        // when
        RecordFormResponse response = recordFormService.getRecordForm(TEACHER_ID, TUTORING_ID, SCHEDULE_ID, WEDNESDAY);

        // then
        assertThat(response.textbooks()).containsExactly(
                new TextbookItem(11L, "수능특강 영어", ProgressUnit.CUSTOM, "강", 7),
                new TextbookItem(12L, "Grammar Zone 기본", ProgressUnit.UNIT, null, 17),
                new TextbookItem(13L, "새 교재", ProgressUnit.UNIT, null, 3));
        assertThat(response.previousHomework()).isNull();
    }

    @ParameterizedTest(name = "{0} → 예측 범위 {1}")
    @CsvSource({
            "2026-09-30, 2026-10-07", // 지난 날짜: 오늘까지
            "2026-10-07, 2026-10-07", // 오늘: 오늘까지
            "2026-10-14, 2026-10-14"  // 미래 날짜: 그 날짜까지 (오늘 이후 회차는 예측으로 계산된다)
    })
    @DisplayName("사이클 타임라인은 그 회차 날짜가 예측 범위에 들도록 만든다")
    void 타임라인_예측_범위(LocalDate originalDate, LocalDate expectedUntil) {
        // given
        givenOpenableLesson(originalDate);

        // when
        recordFormService.getRecordForm(TEACHER_ID, TUTORING_ID, SCHEDULE_ID, originalDate);

        // then
        then(lessonCalendar).should().timelines(List.of(tutoring), expectedUntil);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"없는 과외", "삭제된 과외", "다른 선생님의 과외"})
    @DisplayName("과외가 없거나 삭제됐거나 다른 선생님의 과외면 RECORD404_1")
    void 과외_없음(String situation) {
        // given
        switch (situation) {
            case "없는 과외" -> given(tutoringRepository.findById(TUTORING_ID)).willReturn(Optional.empty());
            case "삭제된 과외" -> {
                tutoring.delete(LocalDateTime.of(2026, 10, 1, 0, 0));
                given(tutoringRepository.findById(TUTORING_ID)).willReturn(Optional.of(tutoring));
            }
            default -> given(tutoringRepository.findById(TUTORING_ID)).willReturn(Optional.of(tutoring));
        }
        Long teacherId = situation.equals("다른 선생님의 과외") ? 2L : TEACHER_ID;

        // when & then
        assertRecordError(teacherId, WEDNESDAY, "RECORD404_1");
    }

    @Test
    @DisplayName("일정이 없으면 RECORD404_2")
    void 일정_없음() {
        // given
        given(tutoringRepository.findById(TUTORING_ID)).willReturn(Optional.of(tutoring));
        given(tutoringScheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.empty());

        // when & then
        assertRecordError(TEACHER_ID, WEDNESDAY, "RECORD404_2");
        then(lessonCalendar).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("다른 과외의 일정이면 RECORD404_2")
    void 다른_과외_일정() {
        // given
        given(tutoringRepository.findById(TUTORING_ID)).willReturn(Optional.of(tutoring));
        TutoringSchedule other = scheduleOf(withId(tutoringOf(null), 99L));
        given(tutoringScheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(other));

        // when & then
        assertRecordError(TEACHER_ID, WEDNESDAY, "RECORD404_2");
    }

    @Test
    @DisplayName("일정의 수업 날짜가 아니면(요일 · 적용 기간 · 과외 기간 밖) RECORD400_1")
    void 수업_날짜_아님() {
        // given
        givenTutoringAndSchedule();
        given(lessonCalendar.findLesson(tutoring, LessonRef.ofSchedule(SCHEDULE_ID, WEDNESDAY)))
                .willReturn(Optional.empty());

        // when & then
        assertRecordError(TEACHER_ID, WEDNESDAY, "RECORD400_1");
    }

    @Test
    @DisplayName("같은 일정 · 날짜로 저장된 회차가 있으면 RECORD409_1이고 사이클 순번을 계산하지 않는다")
    void 저장된_회차() {
        // given
        givenTutoringAndSchedule();
        LessonSlot stored = new LessonSlot(TUTORING_ID, 1199L, SCHEDULE_ID, WEDNESDAY, WEDNESDAY,
                LocalTime.of(18, 0), LocalTime.of(19, 30), SessionType.REGULAR, SessionStatus.CANCELED,
                false, null, false);
        given(lessonCalendar.findLesson(tutoring, LessonRef.ofSchedule(SCHEDULE_ID, WEDNESDAY)))
                .willReturn(Optional.of(stored));

        // when & then
        assertRecordError(TEACHER_ID, WEDNESDAY, "RECORD409_1");
        // 저장 행(미인정 휴강)은 순번이 null이라, 응답을 만들기 전에 막혔는지 확인한다.
        then(lessonCalendar).should(never()).timelines(anyCollection(), any());
    }

    @Test
    @DisplayName("진행 중 사이클이 없으면 RECORD409_2")
    void 진행_중_사이클_없음() {
        // given
        givenTutoringAndSchedule();
        given(lessonCalendar.findLesson(tutoring, LessonRef.ofSchedule(SCHEDULE_ID, WEDNESDAY)))
                .willReturn(Optional.of(planned(WEDNESDAY)));
        given(cycleRepository.findByTutoringIdAndStatus(TUTORING_ID, CycleStatus.IN_PROGRESS))
                .willReturn(Optional.empty());

        // when & then
        assertRecordError(TEACHER_ID, WEDNESDAY, "RECORD409_2");
    }

    // 과외 · 일정 · 회차 · 진행 중 사이클 · 타임라인까지 통과하는 상태. 교재 · 숙제 조회는 테스트마다 정한다.
    private void givenOpenableLesson(LocalDate date) {
        givenTutoringAndSchedule();
        LessonSlot lesson = planned(date);
        given(lessonCalendar.findLesson(tutoring, LessonRef.ofSchedule(SCHEDULE_ID, date)))
                .willReturn(Optional.of(lesson));
        given(cycleRepository.findByTutoringIdAndStatus(TUTORING_ID, CycleStatus.IN_PROGRESS))
                .willReturn(Optional.of(Cycle.open(tutoring, 2, LocalDateTime.of(2026, 9, 1, 0, 0))));
        CycleTimeline timeline = mock(CycleTimeline.class);
        given(timeline.cycleOf(lesson)).willReturn(new SlotCycle(2, 8, 7, 9, false, true));
        given(lessonCalendar.timelines(any(), any())).willReturn(Map.of(TUTORING_ID, timeline));
    }

    private void givenTutoringAndSchedule() {
        given(tutoringRepository.findById(TUTORING_ID)).willReturn(Optional.of(tutoring));
        given(tutoringScheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
    }

    private void assertRecordError(Long teacherId, LocalDate date, String code) {
        assertThatThrownBy(() -> recordFormService.getRecordForm(teacherId, TUTORING_ID, SCHEDULE_ID, date))
                .isInstanceOf(RecordException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo(code);
    }

    // 계산된 예정 회차 (저장 전 정규 회차).
    private static LessonSlot planned(LocalDate date) {
        return new LessonSlot(TUTORING_ID, null, SCHEDULE_ID, date, date, LocalTime.of(18, 0),
                LocalTime.of(19, 30), SessionType.REGULAR, SessionStatus.SCHEDULED, null, null, false);
    }

    private static Tutoring tutoringOf(TeacherProfile teacher) {
        return Tutoring.builder().teacher(teacher).subjects(List.of(Subject.ENGLISH))
                .studentName("윤서아").gradeLevel(9).gradeSchoolYear(2026).build();
    }

    private static TutoringSchedule scheduleOf(Tutoring tutoring) {
        return TutoringSchedule.builder().tutoring(tutoring).dayOfWeek(3)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 30))
                .effectiveFrom(LocalDate.of(2026, 9, 1)).build();
    }

    private Textbook textbookOf(String name, ProgressUnit unit, String customLabel, int startPosition) {
        return Textbook.builder().tutoring(tutoring).name(name).progressUnit(unit)
                .customUnitLabel(customLabel).startPosition(startPosition).build();
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
