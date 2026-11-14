package com.sudam.global.seed;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.invoice.repository.InvoiceRepository;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.participant.repository.ParticipantRepository;
import com.sudam.domain.record.entity.SessionProgress;
import com.sudam.domain.record.entity.SessionRecord;
import com.sudam.domain.record.repository.SessionProgressRepository;
import com.sudam.domain.record.repository.SessionRecordRepository;
import com.sudam.domain.session.entity.Session;
import com.sudam.domain.session.repository.SessionRepository;
import com.sudam.domain.tutoring.entity.Textbook;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import com.sudam.domain.tutoring.entity.TutoringSetting;
import com.sudam.domain.tutoring.enums.ParentUpdateFrequency;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.tutoring.enums.PresetType;
import com.sudam.domain.tutoring.enums.ProgressUnit;
import com.sudam.domain.tutoring.repository.TextbookRepository;
import com.sudam.domain.tutoring.repository.TutoringPolicyRepository;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import com.sudam.domain.tutoring.repository.TutoringScheduleRepository;
import com.sudam.domain.tutoring.repository.TutoringSettingRepository;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.Terms;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.entity.UserTermsAgreement;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import com.sudam.domain.user.enums.TermsCode;
import com.sudam.domain.user.repository.TeacherProfileRepository;
import com.sudam.domain.user.repository.TermsRepository;
import com.sudam.domain.user.repository.UserRepository;
import com.sudam.domain.user.repository.UserTermsAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 시연용 가상 데이터를 만든다. 정산 서비스를 거치지 않고 엔티티를 직접 만들어 저장한다.
 * sudam.seed.enabled = true일 때만 빈이 생긴다 (실행기와 같은 조건).
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "sudam.seed", name = "enabled", havingValue = "true")
public class DemoSeedService {

    private static final String TEACHER_EMAIL = "seed-teacher@sudam.test";
    private static final String TEACHER_NAME = "한서윤";
    private static final String BANK_NAME = "국민은행";
    private static final String BANK_CODE = "004";
    private static final String FAKE_PHONE_PREFIX = "010000000"; // + 두 자리 = 010-0000-00xx
    private static final String FAKE_ACCOUNT_PREFIX = "000000-00-0000"; // + 두 자리
    private static final int LESSON_MINUTES = 90;
    private static final int LINK_TOKEN_BYTES = 32;
    // 수입 화면용 과외는 사이클 1을 5주 전에 열고 1주 전에 정산했다고 둔다 (화면에는 날짜가 드러나지 않음).
    private static final int INCOME_CYCLE_START_WEEKS_AGO = 5;
    private static final int INCOME_CYCLE_SETTLED_DAYS_AGO = 7;

    private final UserRepository userRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final TermsRepository termsRepository;
    private final UserTermsAgreementRepository userTermsAgreementRepository;
    private final TutoringRepository tutoringRepository;
    private final TutoringScheduleRepository tutoringScheduleRepository;
    private final TextbookRepository textbookRepository;
    private final TutoringPolicyRepository tutoringPolicyRepository;
    private final TutoringSettingRepository tutoringSettingRepository;
    private final ParticipantRepository participantRepository;
    private final CycleRepository cycleRepository;
    private final InvoiceRepository invoiceRepository;
    private final SessionRepository sessionRepository;
    private final SessionRecordRepository sessionRecordRepository;
    private final SessionProgressRepository sessionProgressRepository;
    private final Clock clock;

    private final SecureRandom secureRandom = new SecureRandom();

    /***
     * 함수 기능: 시드 선생님 · 온보딩(프로필 · 약관) · 과외 5개를 한 트랜잭션에서 만든다. 중간에 실패하면 전부 롤백된다.
     *          시드 선생님에게 과외가 하나라도 있으면 아무것도 만들지 않는다 (ddl-auto: update에서 반복 부팅해도 한 번만 생김).
     * @param teacherKakaoId 시드 선생님의 카카오 회원번호 (같은 회원이 있으면 그 회원을 쓴다)
     * @return 만들었는지 여부와 시연에 필요한 ID
     */
    @Transactional
    public DemoSeedResult seed(String teacherKakaoId) {
        User teacherUser = userRepository.findByProviderAndProviderId(Provider.KAKAO, teacherKakaoId)
                .orElseGet(() -> userRepository.save(User.createTeacher(Provider.KAKAO, teacherKakaoId, TEACHER_EMAIL)));
        if (tutoringRepository.existsByTeacherId(teacherUser.getId())) {
            return DemoSeedResult.skipped(teacherUser.getId(), teacherUser.getRole());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        TeacherProfile teacher = teacherProfileRepository.findById(teacherUser.getId())
                .orElseGet(() -> teacherProfileRepository.save(
                        TeacherProfile.create(teacherUser, TEACHER_NAME, List.of(Subject.ENGLISH), null)));
        agreeCurrentRequiredTerms(teacherUser, now);

        int seq = 1;
        for (DemoIncomeCard card : DemoIncomeCard.ALL) {
            createIncomeTutoring(teacher, card, seq++, now);
        }
        DemoTutoring demo = createDemoTutoring(teacher, seq, now);

        return DemoSeedResult.created(teacherUser.getId(), teacherUser.getRole(), demo.tutoring().getId(),
                demo.schedules().get(today.getDayOfWeek()).getId(), today);
    }

    // 현재 시행 중인(code별 최신 version) 필수 약관 중 아직 동의하지 않은 것만 동의를 추가한다.
    private void agreeCurrentRequiredTerms(User user, LocalDateTime now) {
        List<Terms> required = termsRepository.findByEffectiveAtLessThanEqual(now).stream()
                .collect(Collectors.toMap(Terms::getCode, Function.identity(),
                        BinaryOperator.maxBy(Comparator.comparingInt(Terms::getVersion))))
                .values().stream()
                .filter(Terms::isRequired)
                .toList();
        if (required.isEmpty()) {
            return;
        }
        Set<Long> agreed = userTermsAgreementRepository.findByUserIdAndTermsIn(user.getId(), required).stream()
                .map(agreement -> agreement.getTerms().getId())
                .collect(Collectors.toSet());
        userTermsAgreementRepository.saveAll(required.stream()
                .filter(terms -> !agreed.contains(terms.getId()))
                .map(terms -> UserTermsAgreement.agree(user, terms))
                .toList());
    }

    // 수입 화면 카드 1장: 사이클 1 SETTLED · 사이클 2 IN_PROGRESS, 사이클 2를 청구하는 선불 청구. 회차 · 기록은 만들지 않는다.
    private void createIncomeTutoring(TeacherProfile teacher, DemoIncomeCard card, int seq, LocalDateTime now) {
        LocalDate cycleStartDate = now.toLocalDate().minusWeeks(INCOME_CYCLE_START_WEEKS_AGO);
        DemoTutoring base = createBase(teacher, card.studentName(), card.gradeLevel(), card.lessonDays(),
                card.lessonStart(), cycleStartDate, card.textbookName(), card.settlementSessionCount(),
                card.tuitionFee(), card.unpaidReminderEnabled(), seq, now);

        LocalDateTime settledAt = now.minusDays(INCOME_CYCLE_SETTLED_DAYS_AGO);
        Cycle first = Cycle.open(base.tutoring(), 1, cycleStartDate.atStartOfDay());
        first.settle(settledAt);
        cycleRepository.save(first);
        Cycle second = cycleRepository.save(Cycle.open(base.tutoring(), 2, settledAt));

        invoiceRepository.save(card.issueInvoice(base.policy(), second.getId(), cycleStartDate, base.parent(), now));
    }

    // 김서아: 사이클 1 IN_PROGRESS에 오늘 전 7회 기록. 오늘 회차 · 청구는 만들지 않는다 (오늘 기록하면 8/8로 정산).
    private DemoTutoring createDemoTutoring(TeacherProfile teacher, int seq, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<DayOfWeek> days = DemoLessonPlan.lessonDays(today);
        List<LocalDate> dates = DemoLessonPlan.pastLessonDates(today, days, DemoLessonPlan.RECORDED_SESSION_COUNT);
        LocalDate firstDate = dates.getFirst();

        DemoTutoring demo = createBase(teacher, DemoLessonPlan.STUDENT_NAME, DemoLessonPlan.GRADE_LEVEL, days,
                DemoLessonPlan.LESSON_START, firstDate, DemoLessonPlan.TEXTBOOK_NAME,
                DemoLessonPlan.SETTLEMENT_SESSION_COUNT, DemoLessonPlan.TUITION_FEE, false, seq, now);
        Cycle cycle = cycleRepository.save(Cycle.open(demo.tutoring(), 1, firstDate.atStartOfDay()));

        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            DemoLessonPlan.LessonNote note = DemoLessonPlan.NOTES.get(i);
            Session session = sessionRepository.save(Session.completeRegular(demo.tutoring(), cycle,
                    demo.schedules().get(date.getDayOfWeek()), date));
            sessionRecordRepository.save(SessionRecord.create(session, note.understanding(),
                    note.previousHomeworkResult(), note.nextHomework(), note.observationMemo()));
            sessionProgressRepository.save(SessionProgress.create(session, demo.textbook(),
                    DemoLessonPlan.progressStart(i), DemoLessonPlan.progressEnd(i)));
        }
        return demo;
    }

    // 과외마다 공통: 과외 · 수업 일정 · 교재 1권 · 정책 · 공유 설정 · 학부모(링크 토큰 발급).
    private DemoTutoring createBase(TeacherProfile teacher, String studentName, int gradeLevel,
                                    List<DayOfWeek> lessonDays, LocalTime lessonStart, LocalDate effectiveFrom,
                                    String textbookName, int settlementSessionCount, int tuitionFee,
                                    boolean unpaidReminderEnabled, int seq, LocalDateTime now) {
        Tutoring tutoring = tutoringRepository.save(Tutoring.builder()
                .teacher(teacher)
                .subject(Subject.ENGLISH)
                .studentName(studentName)
                .gradeLevel(gradeLevel)
                .gradeSchoolYear(DemoLessonPlan.schoolYear(now.toLocalDate()))
                .build());

        Map<DayOfWeek, TutoringSchedule> schedules = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : lessonDays) {
            schedules.put(day, tutoringScheduleRepository.save(TutoringSchedule.builder()
                    .tutoring(tutoring)
                    .dayOfWeek(day.getValue())
                    .startTime(lessonStart)
                    .endTime(lessonStart.plusMinutes(LESSON_MINUTES))
                    .effectiveFrom(effectiveFrom)
                    .build()));
        }

        Textbook textbook = textbookRepository.save(Textbook.builder()
                .tutoring(tutoring)
                .name(textbookName)
                .progressUnit(ProgressUnit.UNIT)
                .startPosition(0)
                .build());

        TutoringPolicy policy = tutoringPolicyRepository.save(TutoringPolicy.builder()
                .tutoring(tutoring)
                .effectiveCycleNo(1)
                .settlementSessionCnt(settlementSessionCount)
                .paymentTiming(PaymentTiming.PREPAID)
                .tuitionFee(tuitionFee)
                .bankCode(BANK_CODE)
                .bankName(BANK_NAME)
                .accountNumber(FAKE_ACCOUNT_PREFIX + twoDigits(seq))
                .accountHolder(TEACHER_NAME)
                .build());

        tutoringSettingRepository.save(TutoringSetting.builder()
                .tutoring(tutoring)
                .presetType(PresetType.SIMPLE)
                .parentUpdateFrequency(ParentUpdateFrequency.AT_SETTLEMENT)
                .homeworkNotifyEnabled(false)
                .unpaidReminderEnabled(unpaidReminderEnabled)
                .build());

        Participant parent = Participant.createParent(tutoring, ParticipantLabel.MOTHER,
                FAKE_PHONE_PREFIX + twoDigits(seq));
        parent.issueLinkToken(newLinkToken(), now);
        participantRepository.save(parent);

        return new DemoTutoring(tutoring, Map.copyOf(schedules), textbook, policy, parent);
    }

    // ERD 공통 규칙: SecureRandom 32바이트를 URL 안전 문자열로.
    private String newLinkToken() {
        byte[] bytes = new byte[LINK_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String twoDigits(int seq) {
        return String.format("%02d", seq);
    }

    private record DemoTutoring(Tutoring tutoring, Map<DayOfWeek, TutoringSchedule> schedules, Textbook textbook,
                                TutoringPolicy policy, Participant parent) {
    }
}
