package com.sudam.domain.tutoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.service.CycleOpener;
import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.service.FirstInvoiceIssuer;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.participant.enums.ParticipantRole;
import com.sudam.domain.participant.service.ParticipantInviter;
import com.sudam.domain.session.service.LessonCalendar;
import com.sudam.domain.session.service.LessonSlot;
import com.sudam.domain.share.service.ShareLinkFactory;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.Account;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.Invite;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.ParentItem;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.Policy;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.Preset;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.ScheduleItem;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.Settings;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.Student;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.TextbookItem;
import com.sudam.domain.tutoring.dto.response.TutoringCreateResponse;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.entity.TutoringSetting;
import com.sudam.domain.tutoring.enums.ParentUpdateFrequency;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.tutoring.enums.PresetType;
import com.sudam.domain.tutoring.enums.ProgressUnit;
import com.sudam.domain.tutoring.exception.code.TutoringErrorCode;
import com.sudam.domain.tutoring.repository.TextbookRepository;
import com.sudam.domain.tutoring.repository.TutoringPolicyRepository;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import com.sudam.domain.tutoring.repository.TutoringScheduleRepository;
import com.sudam.domain.tutoring.repository.TutoringSettingRepository;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.enums.Subject;
import com.sudam.domain.user.repository.TeacherProfileRepository;
import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.GeneralErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TutoringCreateServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-11-27 금요일 10:00 (서울)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-27T01:00:00Z"), SEOUL);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 10, 0);

    @Mock TeacherProfileRepository teacherProfileRepository;
    @Mock TutoringRepository tutoringRepository;
    @Mock TutoringScheduleRepository scheduleRepository;
    @Mock TextbookRepository textbookRepository;
    @Mock TutoringPolicyRepository policyRepository;
    @Mock TutoringSettingRepository settingRepository;
    @Mock CycleOpener cycleOpener;
    @Mock ParticipantInviter participantInviter;
    @Mock FirstInvoiceIssuer firstInvoiceIssuer;
    @Mock LessonCalendar lessonCalendar;
    @Mock ShareLinkFactory shareLinkFactory;
    @Mock TeacherProfile teacher;
    @Mock Cycle cycle;
    @Mock Invoice invoice;

    TutoringCreateService service;

    @BeforeEach
    void setUp() {
        service = new TutoringCreateService(teacherProfileRepository, tutoringRepository, scheduleRepository,
                textbookRepository, policyRepository, settingRepository, cycleOpener, participantInviter,
                firstInvoiceIssuer, lessonCalendar, shareLinkFactory, CLOCK);

        given(teacherProfileRepository.findById(1L)).willReturn(Optional.of(teacher));
        given(teacher.markFirstTutoringCreated(any())).willReturn(true);
        willAnswer(inv -> {
            Tutoring tutoring = inv.getArgument(0);
            ReflectionTestUtils.setField(tutoring, "id", 31L);
            given(tutoringRepository.findByIdForUpdate(31L)).willReturn(Optional.of(tutoring));
            return tutoring;
        }).given(tutoringRepository).save(any(Tutoring.class));
        willAnswer(inv -> inv.getArgument(0)).given(policyRepository).save(any(TutoringPolicy.class));
        given(cycle.getId()).willReturn(88L);
        given(cycleOpener.openNext(any(), any())).willReturn(cycle);
        given(participantInviter.invite(any(), any(), any(), any())).willAnswer(inv -> {
            Participant mother = Participant.createParent(inv.getArgument(0), ParticipantLabel.MOTHER, "01024680525");
            ReflectionTestUtils.setField(mother, "id", 140L);
            mother.issueLinkToken("tokenA", NOW);
            return List.of(mother);
        });
        given(shareLinkFactory.inviteUrl(anyString()))
                .willAnswer(inv -> "https://sudam.app/l/" + inv.getArgument(0));
        given(invoice.getId()).willReturn(502L);
        given(invoice.getAmount()).willReturn(360000);
        given(invoice.getBankCode()).willReturn("004");
        given(invoice.getBankName()).willReturn("국민은행");
        given(invoice.getAccountLast4()).willReturn("1492");
        given(invoice.getAccountHolder()).willReturn("한서윤");
        given(firstInvoiceIssuer.issue(any(), eq(cycle))).willReturn(invoice);
        // 2026-12-01 화요일 17:00
        given(lessonCalendar.lessonsOf(any(), any(), any())).willReturn(List.of(new LessonSlot(31L, null, 1L,
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1), LocalTime.of(17, 0), LocalTime.of(18, 30),
                null, null, null, null, false)));
    }

    // ===== 요청 픽스처 =====

    private static class Fixture {
        Preset preset = new Preset(false, ParentUpdateFrequency.EVERY_SESSION);
        Student student = new Student("박도윤", 8, null, List.of(Subject.MATH), null);
        List<TextbookItem> textbooks = List.of(new TextbookItem("쎈 중2-1", ProgressUnit.SECTION, null, 1));
        List<ScheduleItem> schedules = List.of(new ScheduleItem(2, LocalTime.of(17, 0), LocalTime.of(18, 30)));
        Policy policy = new Policy(8, PaymentTiming.PREPAID, 360000);
        Account account = new Account("국민은행", "123456-01-401492", "한서윤");
        Settings settings = new Settings(ParentUpdateFrequency.EVERY_SESSION, true, false);
        Invite invite = new Invite(List.of(new ParentItem("010-2468-0525", ParticipantLabel.MOTHER)), "010-5555-0192");
        Long draftId = null;

        TutoringCreateRequest build() {
            return new TutoringCreateRequest(preset, student, textbooks, schedules, policy, account, settings,
                    invite, draftId, null, null);
        }
    }

    private TutoringCreateRequest request(UnaryOperator<Fixture> customizer) {
        return customizer.apply(new Fixture()).build();
    }

    private TutoringCreateRequest request() {
        return request(f -> f);
    }

    private void assertRejected(TutoringCreateRequest request, TutoringErrorCode expected) {
        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOfSatisfying(ProjectException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo(expected.getCode()));
        verify(tutoringRepository, never()).save(any());
    }

    // ===== 교차 검증 =====

    @Test
    @DisplayName("미룬 필드가 오면 UNSUPPORTED_FIELD")
    void 미룬_필드_거절() {
        assertRejected(request(f -> { f.draftId = 5L; return f; }), TutoringErrorCode.UNSUPPORTED_FIELD);
    }

    @Test
    @DisplayName("건너뛰지 않았는데 근황 답이 없으면 INVALID_INPUT")
    void 근황_답_누락() {
        assertRejected(request(f -> { f.preset = new Preset(false, null); return f; }),
                TutoringErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("기타 학년인데 gradeOther가 없거나 10자를 넘으면 INVALID_INPUT")
    void 학년_기타_검증() {
        assertRejected(request(f -> {
            f.student = new Student("박도윤", 0, null, List.of(Subject.MATH), null);
            return f;
        }), TutoringErrorCode.INVALID_INPUT);
        assertRejected(request(f -> {
            f.student = new Student("박도윤", 0, "12345678901", List.of(Subject.MATH), null);
            return f;
        }), TutoringErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("과목이 중복이면 INVALID_INPUT")
    void 과목_중복() {
        assertRejected(request(f -> {
            f.student = new Student("박도윤", 8, null, List.of(Subject.MATH, Subject.MATH), null);
            return f;
        }), TutoringErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("OTHER 과목인데 subjectOther가 없으면 INVALID_INPUT")
    void 과목_기타_누락() {
        assertRejected(request(f -> {
            f.student = new Student("박도윤", 8, null, List.of(Subject.OTHER), null);
            return f;
        }), TutoringErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("CUSTOM 단위인데 label이 없거나 5자를 넘으면 INVALID_INPUT")
    void 교재_단위_검증() {
        assertRejected(request(f -> {
            f.textbooks = List.of(new TextbookItem("a", ProgressUnit.CUSTOM, null, 0));
            return f;
        }), TutoringErrorCode.INVALID_INPUT);
        assertRejected(request(f -> {
            f.textbooks = List.of(new TextbookItem("a", ProgressUnit.CUSTOM, "123456", 0));
            return f;
        }), TutoringErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("요일이 중복이면 SCHEDULE_DUPLICATE_DAY")
    void 요일_중복() {
        assertRejected(request(f -> {
            f.schedules = List.of(new ScheduleItem(2, LocalTime.of(17, 0), LocalTime.of(18, 0)),
                    new ScheduleItem(2, LocalTime.of(19, 0), LocalTime.of(20, 0)));
            return f;
        }), TutoringErrorCode.SCHEDULE_DUPLICATE_DAY);
    }

    @Test
    @DisplayName("10분 단위가 아니거나 초가 있거나 시작이 종료 이상이면 INVALID_LESSON_TIME")
    void 수업_시간_검증() {
        assertRejected(request(f -> {
            f.schedules = List.of(new ScheduleItem(2, LocalTime.of(17, 5), LocalTime.of(18, 0)));
            return f;
        }), TutoringErrorCode.INVALID_LESSON_TIME);
        assertRejected(request(f -> {
            f.schedules = List.of(new ScheduleItem(2, LocalTime.of(17, 0, 30), LocalTime.of(18, 0)));
            return f;
        }), TutoringErrorCode.INVALID_LESSON_TIME);
        assertRejected(request(f -> {
            f.schedules = List.of(new ScheduleItem(2, LocalTime.of(18, 0), LocalTime.of(18, 0)));
            return f;
        }), TutoringErrorCode.INVALID_LESSON_TIME);
    }

    @Test
    @DisplayName("정규화한 번호가 학부모끼리 또는 학부모 · 학생 사이에 겹치면 DUPLICATE_PHONE")
    void 번호_중복() {
        assertRejected(request(f -> {
            f.invite = new Invite(List.of(new ParentItem("010-2468-0525", null),
                    new ParentItem("01024680525", null)), null);
            return f;
        }), TutoringErrorCode.DUPLICATE_PHONE);
        assertRejected(request(f -> {
            f.invite = new Invite(List.of(new ParentItem("010-2468-0525", null)), "01024680525");
            return f;
        }), TutoringErrorCode.DUPLICATE_PHONE);
    }

    @Test
    @DisplayName("프로필이 없으면 TEACHER_PROFILE_NOT_FOUND이고 저장하지 않는다")
    void 프로필_없음() {
        given(teacherProfileRepository.findById(1L)).willReturn(Optional.empty());

        assertRejected(request(), TutoringErrorCode.TEACHER_PROFILE_NOT_FOUND);
    }

    // ===== 정상 흐름 =====

    @Test
    @DisplayName("선불이면 첫 청구를 발행하고 응답에 채운다")
    void 선불_첫_청구() {
        // given / when
        TutoringCreateResponse response = service.create(1L, request());

        // then
        verify(firstInvoiceIssuer).issue(any(), eq(cycle));
        assertThat(response.firstInvoice().invoiceId()).isEqualTo(502L);
        assertThat(response.firstInvoice().invoiceName()).isEqualTo("첫 8회차");
        assertThat(response.firstInvoice().amount()).isEqualTo(360000);
        assertThat(response.firstInvoice().accountLast4()).isEqualTo("1492");
    }

    @Test
    @DisplayName("후불이면 첫 청구를 발행하지 않고 firstInvoice는 null")
    void 후불_청구_없음() {
        // given / when
        TutoringCreateResponse response = service.create(1L, request(f -> {
            f.policy = new Policy(8, PaymentTiming.POSTPAID, 360000);
            return f;
        }));

        // then
        verify(firstInvoiceIssuer, never()).issue(any(), any());
        assertThat(response.firstInvoice()).isNull();
    }

    @Test
    @DisplayName("학생 번호가 없으면 숙제 알림은 false로 저장하고 inviter에 null을 넘긴다")
    void 학생_번호_없음() {
        // given
        TutoringCreateRequest request = request(f -> {
            f.invite = new Invite(List.of(new ParentItem("010-2468-0525", ParticipantLabel.MOTHER)), null);
            return f;
        });

        // when
        service.create(1L, request);

        // then
        ArgumentCaptor<TutoringSetting> setting = ArgumentCaptor.forClass(TutoringSetting.class);
        verify(settingRepository).save(setting.capture());
        assertThat(setting.getValue().isHomeworkNotifyEnabled()).isFalse();
        verify(participantInviter).invite(any(), any(), eq(null), eq(NOW));
    }

    @Test
    @DisplayName("국민은행은 코드 004, 목록에 없는 KB는 코드 없이 입력값 그대로 저장한다")
    void 은행_코드() {
        // given / when
        service.create(1L, request());
        service.create(1L, request(f -> {
            f.account = new Account("KB", "123456-01-401492", "한서윤");
            return f;
        }));

        // then
        ArgumentCaptor<TutoringPolicy> policy = ArgumentCaptor.forClass(TutoringPolicy.class);
        verify(policyRepository, times(2)).save(policy.capture());
        assertThat(policy.getAllValues().get(0).getBankCode()).isEqualTo("004");
        assertThat(policy.getAllValues().get(1).getBankCode()).isNull();
        assertThat(policy.getAllValues().get(1).getBankName()).isEqualTo("KB");
    }

    @Test
    @DisplayName("첫 과외 여부는 프로필 기록 결과를 따른다")
    void 첫_과외_여부() {
        assertThat(service.create(1L, request()).isFirstTutoring()).isTrue();

        given(teacher.markFirstTutoringCreated(any())).willReturn(false);
        assertThat(service.create(1L, request()).isFirstTutoring()).isFalse();
    }

    @Test
    @DisplayName("과외 행을 잠근 뒤에 사이클을 연다")
    void 잠금_후_사이클() {
        // given / when
        service.create(1L, request());

        // then
        InOrder order = inOrder(tutoringRepository, cycleOpener);
        order.verify(tutoringRepository).findByIdForUpdate(31L);
        order.verify(cycleOpener).openNext(any(), eq(NOW));
    }

    @Test
    @DisplayName("응답 값은 유형 · 첫 수업 · 참여자 링크와 고정 값을 담는다")
    void 응답_조립() {
        // given / when
        TutoringCreateResponse response = service.create(1L, request());

        // then
        assertThat(response.tutoringId()).isEqualTo(31L);
        assertThat(response.cycleId()).isEqualTo(88L);
        assertThat(response.presetType()).isEqualTo(PresetType.THOROUGH);
        assertThat(response.studentInviteQueued()).isFalse();
        assertThat(response.hasPlan()).isFalse();
        assertThat(response.firstLesson().date()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(response.firstLesson().dayOfWeek()).isEqualTo(2);
        assertThat(response.firstLesson().startTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().get(0).participantId()).isEqualTo(140L);
        assertThat(response.participants().get(0).role()).isEqualTo(ParticipantRole.PARENT);
        assertThat(response.participants().get(0).label()).isEqualTo(ParticipantLabel.MOTHER);
        assertThat(response.participants().get(0).linkUrl()).isEqualTo("https://sudam.app/l/tokenA");
    }

    @Test
    @DisplayName("첫 수업을 찾지 못하면 서버 오류")
    void 첫_수업_없음() {
        given(lessonCalendar.lessonsOf(any(), any(), any())).willReturn(List.of());

        assertThatThrownBy(() -> service.create(1L, request()))
                .isInstanceOfSatisfying(ProjectException.class, e -> assertThat(e.getErrorCode().getCode())
                        .isEqualTo(GeneralErrorCode.INTERNAL_SERVER_ERROR.getCode()));
    }
}
