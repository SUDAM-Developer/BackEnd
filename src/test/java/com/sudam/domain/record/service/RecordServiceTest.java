package com.sudam.domain.record.service;

import com.sudam.domain.settlement.dto.SettlementResult;
import com.sudam.domain.settlement.service.SettlementService;
import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.enums.CycleStatus;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.record.dto.request.SaveRecordRequest;
import com.sudam.domain.record.dto.request.SaveRecordRequest.ProgressRequest;
import com.sudam.domain.record.dto.response.SaveRecordResponse;
import com.sudam.domain.record.entity.SessionRecord;
import com.sudam.domain.record.enums.HomeworkResult;
import com.sudam.domain.record.enums.Understanding;
import com.sudam.domain.record.exception.RecordException;
import com.sudam.domain.record.repository.SessionProgressRepository;
import com.sudam.domain.record.repository.SessionRecordRepository;
import com.sudam.domain.session.entity.Session;
import com.sudam.domain.session.repository.SessionRepository;
import com.sudam.domain.tutoring.entity.Textbook;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import com.sudam.domain.tutoring.enums.ProgressUnit;
import com.sudam.domain.tutoring.repository.TextbookRepository;
import com.sudam.domain.tutoring.repository.TutoringPolicyRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordService 수업 기록 저장")
class RecordServiceTest {

    private static final Long TEACHER_ID = 1L;
    private static final Long TUTORING_ID = 10L;
    private static final Long SCHEDULE_ID = 31L;
    private static final Long TEXTBOOK_ID = 11L;
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 30);

    @Mock private TutoringRepository tutoringRepository;
    @Mock private TutoringScheduleRepository tutoringScheduleRepository;
    @Mock private TextbookRepository textbookRepository;
    @Mock private TutoringPolicyRepository tutoringPolicyRepository;
    @Mock private CycleRepository cycleRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private SessionRecordRepository sessionRecordRepository;
    @Mock private SessionProgressRepository sessionProgressRepository;
    @Mock private SettlementService settlementService;

    @InjectMocks
    private RecordService recordService;

    private Tutoring tutoring;
    private TutoringSchedule schedule;
    private Textbook textbook;
    private Cycle cycle;

    @BeforeEach
    void setUp() {
        TeacherProfile teacher = withId(TeacherProfile.create(
                User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app"),
                "한서윤", List.of(Subject.MATH), null), TEACHER_ID);
        tutoring = withId(tutoringOf(teacher), TUTORING_ID);
        schedule = withId(scheduleOf(tutoring), SCHEDULE_ID);
        textbook = withId(Textbook.builder().tutoring(tutoring).name("개념원리")
                .progressUnit(ProgressUnit.SECTION).build(), TEXTBOOK_ID);
        cycle = withId(Cycle.open(tutoring, 1, LocalDateTime.of(2026, 9, 1, 0, 0)), 88L);
    }

    @Test
    @DisplayName("정규 수업을 기록하면 회차 · 기록 · 진도를 저장하고 사이클 진행 현황을 돌려준다")
    void 정규_수업_기록_성공() {
        // given
        givenTutoring();
        givenSchedule();
        givenTextbook();
        given(sessionRepository.existsByScheduleIdAndOriginalDate(SCHEDULE_ID, WEDNESDAY)).willReturn(false);
        given(cycleRepository.findByTutoringIdAndStatus(TUTORING_ID, CycleStatus.IN_PROGRESS))
                .willReturn(Optional.of(cycle));
        List<Session> saved = new ArrayList<>();
        given(sessionRepository.save(any(Session.class))).willAnswer(invocation -> {
            Session session = withId(invocation.<Session>getArgument(0), 1198L);
            saved.add(session);
            return session;
        });
        Session previous = Session.completeRegular(tutoring, cycle, schedule, WEDNESDAY.minusWeeks(1));
        given(sessionRepository.findRecognizedInCycle(88L)).willAnswer(invocation -> List.of(previous, saved.get(0)));
        given(settlementService.settleIfReached(TUTORING_ID)).willReturn(SettlementResult.notSettled());
        TutoringPolicy policy = mock(TutoringPolicy.class);
        given(policy.getEffectiveCycleNo()).willReturn(1);
        given(policy.getSettlementSessionCnt()).willReturn(8);
        given(tutoringPolicyRepository.findAllByTutoringId(TUTORING_ID)).willReturn(List.of(policy));

        // when
        SaveRecordResponse response = recordService.save(TEACHER_ID, TUTORING_ID, request(WEDNESDAY, 7, 7));

        // then
        assertThat(response.sessionId()).isEqualTo(1198L);
        assertThat(response.noInCycle()).isEqualTo(2);
        assertThat(response.cycle()).isEqualTo(new SaveRecordResponse.CycleSummary(1, 2, 8));
        assertThat(response.settlement()).isEqualTo(new SaveRecordResponse.Settlement(false, null, null, null));
        assertThat(saved.get(0).getCycle()).isSameAs(cycle);
        verify(sessionRecordRepository).save(any(SessionRecord.class));
        verify(sessionProgressRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("다른 선생님의 과외면 RECORD404_1")
    void 다른_선생님_과외() {
        givenTutoring();
        assertRecordError(2L, request(WEDNESDAY, 7, 7), "RECORD404_1");
    }

    @Test
    @DisplayName("다른 과외의 일정이면 RECORD404_2")
    void 다른_과외_일정() {
        givenTutoring();
        TutoringSchedule otherSchedule = scheduleOf(withId(tutoringOf(null), 99L));
        given(tutoringScheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(otherSchedule));
        assertRecordError(TEACHER_ID, request(WEDNESDAY, 7, 7), "RECORD404_2");
    }

    @Test
    @DisplayName("미래 날짜면 RECORD400_2")
    void 미래_날짜() {
        givenTutoring();
        givenSchedule();
        assertRecordError(TEACHER_ID, request(LocalDate.of(2999, 1, 1), 7, 7), "RECORD400_2");
    }

    @Test
    @DisplayName("일정 요일과 다른 날짜면 RECORD400_1")
    void 요일_불일치() {
        givenTutoring();
        givenSchedule();
        assertRecordError(TEACHER_ID, request(WEDNESDAY.plusDays(1), 7, 7), "RECORD400_1");
    }

    @Test
    @DisplayName("시작 진도가 끝 진도보다 크면 RECORD400_3")
    void 진도_범위_역전() {
        givenTutoring();
        givenSchedule();
        assertRecordError(TEACHER_ID, request(WEDNESDAY, 8, 7), "RECORD400_3");
    }

    @Test
    @DisplayName("이 과외의 교재가 아니면 RECORD404_3")
    void 다른_과외_교재() {
        givenTutoring();
        givenSchedule();
        given(textbookRepository.findAllById(List.of(TEXTBOOK_ID))).willReturn(List.of());
        assertRecordError(TEACHER_ID, request(WEDNESDAY, 7, 7), "RECORD404_3");
    }

    @Test
    @DisplayName("이미 기록한 수업이면 RECORD409_1이고 아무것도 저장하지 않는다")
    void 이미_기록한_수업() {
        givenTutoring();
        givenSchedule();
        givenTextbook();
        given(sessionRepository.existsByScheduleIdAndOriginalDate(SCHEDULE_ID, WEDNESDAY)).willReturn(true);

        assertRecordError(TEACHER_ID, request(WEDNESDAY, 7, 7), "RECORD409_1");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("정산 회차를 채우면 정산 결과(사이클 · 리포트 · 청구 ID)를 응답에 싣고, 현황은 닫은 사이클 기준이다")
    void 정산_회차_도달() {
        // given
        givenTutoring();
        givenSchedule();
        givenTextbook();
        given(sessionRepository.existsByScheduleIdAndOriginalDate(SCHEDULE_ID, WEDNESDAY)).willReturn(false);
        given(cycleRepository.findByTutoringIdAndStatus(TUTORING_ID, CycleStatus.IN_PROGRESS))
                .willReturn(Optional.of(cycle));
        List<Session> saved = new ArrayList<>();
        given(sessionRepository.save(any(Session.class))).willAnswer(invocation -> {
            Session session = withId(invocation.<Session>getArgument(0), 1198L);
            saved.add(session);
            return session;
        });
        Session previous = Session.completeRegular(tutoring, cycle, schedule, WEDNESDAY.minusWeeks(1));
        given(sessionRepository.findRecognizedInCycle(88L)).willAnswer(invocation -> List.of(previous, saved.get(0)));
        TutoringPolicy policy = mock(TutoringPolicy.class);
        given(policy.getEffectiveCycleNo()).willReturn(1);
        given(policy.getSettlementSessionCnt()).willReturn(2);
        given(tutoringPolicyRepository.findAllByTutoringId(TUTORING_ID)).willReturn(List.of(policy));
        given(settlementService.settleIfReached(TUTORING_ID)).willReturn(SettlementResult.settled(88L, 21L, 301L));

        // when
        SaveRecordResponse response = recordService.save(TEACHER_ID, TUTORING_ID, request(WEDNESDAY, 7, 7));

        // then
        assertThat(response.noInCycle()).isEqualTo(2);
        assertThat(response.cycle()).isEqualTo(new SaveRecordResponse.CycleSummary(1, 2, 2));
        assertThat(response.settlement()).isEqualTo(new SaveRecordResponse.Settlement(true, 88L, 21L, 301L));
    }

    @Test
    @DisplayName("진행 중 사이클이 없으면 RECORD409_2")
    void 진행_중_사이클_없음() {
        givenTutoring();
        givenSchedule();
        givenTextbook();
        given(sessionRepository.existsByScheduleIdAndOriginalDate(SCHEDULE_ID, WEDNESDAY)).willReturn(false);
        given(cycleRepository.findByTutoringIdAndStatus(TUTORING_ID, CycleStatus.IN_PROGRESS))
                .willReturn(Optional.empty());
        assertRecordError(TEACHER_ID, request(WEDNESDAY, 7, 7), "RECORD409_2");
    }

    private void givenTutoring() {
        given(tutoringRepository.findById(TUTORING_ID)).willReturn(Optional.of(tutoring));
    }

    private void givenSchedule() {
        given(tutoringScheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
    }

    private void givenTextbook() {
        given(textbookRepository.findAllById(List.of(TEXTBOOK_ID))).willReturn(List.of(textbook));
    }

    private void assertRecordError(Long teacherId, SaveRecordRequest request, String code) {
        assertThatThrownBy(() -> recordService.save(teacherId, TUTORING_ID, request))
                .isInstanceOf(RecordException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo(code);
    }

    private static SaveRecordRequest request(LocalDate date, int start, int end) {
        return new SaveRecordRequest(SCHEDULE_ID, date,
                List.of(new ProgressRequest(TEXTBOOK_ID, start, end)),
                Understanding.HIGH, HomeworkResult.DONE, "7강 예습", null);
    }

    private static Tutoring tutoringOf(TeacherProfile teacher) {
        return Tutoring.builder().teacher(teacher).subject(Subject.MATH)
                .studentName("김민준").gradeLevel(8).gradeSchoolYear(2026).build();
    }

    private static TutoringSchedule scheduleOf(Tutoring tutoring) {
        return TutoringSchedule.builder().tutoring(tutoring).dayOfWeek(3)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 30))
                .effectiveFrom(LocalDate.of(2026, 9, 1)).build();
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
