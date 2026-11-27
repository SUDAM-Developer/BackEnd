package com.sudam.domain.tutoring.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.service.CycleOpener;
import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.service.FirstInvoiceIssuer;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.service.ParentContact;
import com.sudam.domain.participant.service.ParticipantInviter;
import com.sudam.domain.session.service.LessonCalendar;
import com.sudam.domain.session.service.LessonSlot;
import com.sudam.domain.share.service.ShareLinkFactory;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.ScheduleItem;
import com.sudam.domain.tutoring.dto.response.TutoringCreateResponse;
import com.sudam.domain.tutoring.entity.Textbook;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import com.sudam.domain.tutoring.entity.TutoringSetting;
import com.sudam.domain.tutoring.enums.Bank;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.tutoring.enums.PresetType;
import com.sudam.domain.tutoring.enums.ProgressUnit;
import com.sudam.domain.tutoring.exception.TutoringException;
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
import com.sudam.global.util.SchoolYear;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 과외 만들기. 과외 · 일정 · 교재 · 정책 · 설정 · 사이클 1 · 참여자 · (선불) 첫 청구를 한 트랜잭션으로 만든다.
 */
@Service
@RequiredArgsConstructor
public class TutoringCreateService {

    private static final int OTHER_GRADE_LEVEL = 0;
    private static final int MAX_OTHER_TEXT_LENGTH = 10;
    private static final int MAX_CUSTOM_UNIT_LENGTH = 5;
    private static final int LESSON_MINUTE_STEP = 10;
    private static final int FIRST_LESSON_SEARCH_DAYS = 7;

    private final TeacherProfileRepository teacherProfileRepository;
    private final TutoringRepository tutoringRepository;
    private final TutoringScheduleRepository scheduleRepository;
    private final TextbookRepository textbookRepository;
    private final TutoringPolicyRepository policyRepository;
    private final TutoringSettingRepository settingRepository;
    private final CycleOpener cycleOpener;
    private final ParticipantInviter participantInviter;
    private final FirstInvoiceIssuer firstInvoiceIssuer;
    private final LessonCalendar lessonCalendar;
    private final ShareLinkFactory shareLinkFactory;
    private final Clock clock;

    /***
     * 함수 기능: 과외 하나에 필요한 모든 행을 만들고 5/5 이후 화면에 쓸 값을 돌려준다.
     * @param teacherId 선생님 회원 ID (= teacher_profiles PK)
     * @param request   과외 만들기 요청
     * @return 생성 결과
     * @throws TutoringException 교차 검증 실패(400) · 프로필 없음(404)
     */
    @Transactional
    public TutoringCreateResponse create(Long teacherId, TutoringCreateRequest request) {
        validate(request);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        // 같은 선생님의 첫 과외가 동시에 만들어져 isFirstTutoring이 둘 다 true가 되거나 교착되지 않도록 프로필을 먼저 잠근다 (잠금 순서: 프로필 → 과외).
        TeacherProfile teacher = teacherProfileRepository.findByIdForUpdate(teacherId)
                .orElseThrow(() -> new TutoringException(TutoringErrorCode.TEACHER_PROFILE_NOT_FOUND));

        Tutoring saved = tutoringRepository.save(buildTutoring(teacher, request, today));
        // CycleOpener 계약: 사이클을 열기 전에 과외 행을 잠근다. 방금 저장한 행이라 없을 수 없다.
        Tutoring tutoring = tutoringRepository.findByIdForUpdate(saved.getId())
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.INTERNAL_SERVER_ERROR));

        saveSchedules(tutoring, request, today);
        saveTextbooks(tutoring, request);
        TutoringPolicy policy = policyRepository.save(buildPolicy(tutoring, request));
        PresetType presetType = saveSetting(tutoring, request);

        Cycle cycle = cycleOpener.openNext(tutoring, now);
        List<Participant> participants = participantInviter.invite(tutoring, parentContacts(request),
                request.invite().normalizedStudentPhone().orElse(null), now);

        Optional<Invoice> firstInvoice = request.policy().paymentTiming() == PaymentTiming.PREPAID
                ? Optional.of(firstInvoiceIssuer.issue(policy, cycle))
                : Optional.empty();

        boolean isFirstTutoring = teacher.markFirstTutoringCreated(now);
        TutoringCreateResponse.FirstLesson firstLesson = findFirstLesson(tutoring, today);

        return new TutoringCreateResponse(
                tutoring.getId(),
                cycle.getId(),
                presetType,
                isFirstTutoring,
                firstLesson,
                participants.stream().map(this::toParticipantItem).toList(),
                false,
                firstInvoice.map(invoice -> toFirstInvoice(invoice, request.policy().settlementSessionCount()))
                        .orElse(null),
                false);
    }

    // ===== 교차 검증 (저장 전에 모두) =====

    private void validate(TutoringCreateRequest request) {
        if (request.draftId() != null || request.consultStudentId() != null || request.curriculumId() != null) {
            throw new TutoringException(TutoringErrorCode.UNSUPPORTED_FIELD);
        }
        validatePreset(request.preset());
        validateStudent(request.student());
        request.textbooks().forEach(this::validateTextbook);
        validateSchedules(request.schedules());
        validatePhones(request);
    }

    private void validatePreset(TutoringCreateRequest.Preset preset) {
        if (!preset.skipped() && preset.parentUpdateAnswer() == null) {
            throw invalidInput();
        }
    }

    private void validateStudent(TutoringCreateRequest.Student student) {
        if (student.gradeLevel() == OTHER_GRADE_LEVEL && !hasText(student.gradeOther(), MAX_OTHER_TEXT_LENGTH)) {
            throw invalidInput();
        }
        List<Subject> subjects = student.subjects();
        if (new HashSet<>(subjects).size() != subjects.size()) {
            throw invalidInput();
        }
        if (subjects.contains(Subject.OTHER) && !hasText(student.subjectOther(), MAX_OTHER_TEXT_LENGTH)) {
            throw invalidInput();
        }
    }

    private void validateTextbook(TutoringCreateRequest.TextbookItem textbook) {
        if (textbook.progressUnit() == ProgressUnit.CUSTOM
                && !hasText(textbook.customUnitLabel(), MAX_CUSTOM_UNIT_LENGTH)) {
            throw invalidInput();
        }
    }

    private void validateSchedules(List<ScheduleItem> schedules) {
        Set<Integer> days = new HashSet<>();
        for (ScheduleItem schedule : schedules) {
            if (!days.add(schedule.dayOfWeek())) {
                throw new TutoringException(TutoringErrorCode.SCHEDULE_DUPLICATE_DAY);
            }
        }
        for (ScheduleItem schedule : schedules) {
            if (!isValidLessonTime(schedule.startTime(), schedule.endTime())) {
                throw new TutoringException(TutoringErrorCode.INVALID_LESSON_TIME);
            }
        }
    }

    private boolean isValidLessonTime(LocalTime start, LocalTime end) {
        return isTenMinuteUnit(start) && isTenMinuteUnit(end) && start.isBefore(end);
    }

    private boolean isTenMinuteUnit(LocalTime time) {
        return time.getMinute() % LESSON_MINUTE_STEP == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }

    private void validatePhones(TutoringCreateRequest request) {
        Set<String> phones = new HashSet<>();
        for (TutoringCreateRequest.ParentItem parent : request.invite().parents()) {
            if (!phones.add(parent.normalizedPhone())) {
                throw new TutoringException(TutoringErrorCode.DUPLICATE_PHONE);
            }
        }
        if (request.invite().normalizedStudentPhone().map(phone -> !phones.add(phone)).orElse(false)) {
            throw new TutoringException(TutoringErrorCode.DUPLICATE_PHONE);
        }
    }

    private boolean hasText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private TutoringException invalidInput() {
        return new TutoringException(TutoringErrorCode.INVALID_INPUT);
    }

    // ===== 엔티티 조립 =====

    private Tutoring buildTutoring(TeacherProfile teacher, TutoringCreateRequest request, LocalDate today) {
        TutoringCreateRequest.Student student = request.student();
        // 조건이 아닌 기타 칸은 NULL로 저장한다.
        return Tutoring.builder()
                .teacher(teacher)
                .subjects(student.subjects())
                .subjectOther(student.subjects().contains(Subject.OTHER) ? student.subjectOther() : null)
                .studentName(student.name())
                .gradeLevel(student.gradeLevel())
                .gradeOther(student.gradeLevel() == OTHER_GRADE_LEVEL ? student.gradeOther() : null)
                .gradeSchoolYear(SchoolYear.of(today))
                .build();
    }

    private void saveSchedules(Tutoring tutoring, TutoringCreateRequest request, LocalDate today) {
        List<TutoringSchedule> schedules = request.schedules().stream()
                .map(item -> TutoringSchedule.builder()
                        .tutoring(tutoring)
                        .dayOfWeek(item.dayOfWeek())
                        .startTime(item.startTime())
                        .endTime(item.endTime())
                        .effectiveFrom(today)
                        .build())
                .toList();
        scheduleRepository.saveAll(schedules);
    }

    private void saveTextbooks(Tutoring tutoring, TutoringCreateRequest request) {
        List<Textbook> textbooks = request.textbooks().stream()
                .map(item -> Textbook.builder()
                        .tutoring(tutoring)
                        .name(item.name())
                        .progressUnit(item.progressUnit())
                        .customUnitLabel(item.progressUnit() == ProgressUnit.CUSTOM ? item.customUnitLabel() : null)
                        .startPosition(item.startPosition())
                        .build())
                .toList();
        textbookRepository.saveAll(textbooks);
    }

    private TutoringPolicy buildPolicy(Tutoring tutoring, TutoringCreateRequest request) {
        TutoringCreateRequest.Policy policy = request.policy();
        TutoringCreateRequest.Account account = request.account();
        // 목록에 없는 은행은 bankCode NULL, 입력한 이름은 그대로 둔다.
        String bankName = account.bankName().strip();
        String bankCode = BankCatalog.find(bankName).map(Bank::getCode).orElse(null);
        return TutoringPolicy.builder()
                .tutoring(tutoring)
                .effectiveCycleNo(1)
                .settlementSessionCnt(policy.settlementSessionCount())
                .paymentTiming(policy.paymentTiming())
                .tuitionFee(policy.tuitionFee())
                .bankCode(bankCode)
                .bankName(bankName)
                .accountNumber(account.accountNumber())
                .accountHolder(account.accountHolder())
                .build();
    }

    private PresetType saveSetting(Tutoring tutoring, TutoringCreateRequest request) {
        PresetType presetType = PresetJudge.judge(request.preset().skipped(), request.preset().parentUpdateAnswer());
        TutoringCreateRequest.Settings settings = request.settings();
        // 학생 번호가 없으면 숙제 알림은 보낼 곳이 없어 꺼서 저장한다.
        boolean homeworkNotify = request.invite().normalizedStudentPhone().isPresent()
                && Boolean.TRUE.equals(settings.homeworkNotifyEnabled());
        settingRepository.save(TutoringSetting.builder()
                .tutoring(tutoring)
                .presetType(presetType)
                .parentUpdateFrequency(settings.parentUpdateFrequency())
                .homeworkNotifyEnabled(homeworkNotify)
                .unpaidReminderEnabled(Boolean.TRUE.equals(settings.unpaidReminderEnabled()))
                .build());
        return presetType;
    }

    private List<ParentContact> parentContacts(TutoringCreateRequest request) {
        List<ParentContact> contacts = new ArrayList<>();
        for (TutoringCreateRequest.ParentItem parent : request.invite().parents()) {
            contacts.add(new ParentContact(parent.label(), parent.normalizedPhone()));
        }
        return contacts;
    }

    // ===== 응답 조립 =====

    private TutoringCreateResponse.FirstLesson findFirstLesson(Tutoring tutoring, LocalDate today) {
        List<LessonSlot> slots = lessonCalendar.lessonsOf(List.of(tutoring), today,
                today.plusDays(FIRST_LESSON_SEARCH_DAYS));
        // 일정이 1개 이상이면 7일 안에 반드시 하나 있다. 없으면 버그.
        LessonSlot first = slots.stream().findFirst()
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.INTERNAL_SERVER_ERROR));
        return new TutoringCreateResponse.FirstLesson(first.date(), first.date().getDayOfWeek().getValue(),
                first.startTime());
    }

    private TutoringCreateResponse.ParticipantItem toParticipantItem(Participant participant) {
        return new TutoringCreateResponse.ParticipantItem(participant.getId(), participant.getRole(),
                participant.getLabel(), shareLinkFactory.inviteUrl(participant.getLinkToken()));
    }

    private TutoringCreateResponse.FirstInvoice toFirstInvoice(Invoice invoice, int settlementSessionCount) {
        return new TutoringCreateResponse.FirstInvoice(invoice.getId(), "첫 " + settlementSessionCount + "회차",
                invoice.getAmount(), invoice.getBankCode(), invoice.getBankName(), invoice.getAccountLast4(),
                invoice.getAccountHolder());
    }
}
