package com.sudam.domain.tutoring.dto.request;

import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.tutoring.enums.ParentUpdateFrequency;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.tutoring.enums.ProgressUnit;
import com.sudam.domain.user.enums.Subject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

// 필드 단위 검증만 여기서 한다. 필드를 넘나드는 규칙(기타 칸, 요일 중복, 번호 중복 등)은 서비스에서 TutoringException으로 던진다.
public record TutoringCreateRequest(

        @NotNull(message = "운영 방식을 입력해 주세요.")
        @Valid
        Preset preset,

        @NotNull(message = "학생 정보를 입력해 주세요.")
        @Valid
        Student student,

        @NotNull(message = "교재를 입력해 주세요.")
        @Size(min = 1, max = 5, message = "교재는 1~5권까지 입력할 수 있습니다.")
        List<@NotNull(message = "교재 정보를 입력해 주세요.") @Valid TextbookItem> textbooks,

        @NotEmpty(message = "수업 일정을 하나 이상 입력해 주세요.")
        List<@NotNull(message = "수업 일정을 입력해 주세요.") @Valid ScheduleItem> schedules,

        @NotNull(message = "정산 정보를 입력해 주세요.")
        @Valid
        Policy policy,

        @NotNull(message = "계좌 정보를 입력해 주세요.")
        @Valid
        Account account,

        @NotNull(message = "알림 설정을 입력해 주세요.")
        @Valid
        Settings settings,

        @NotNull(message = "초대 정보를 입력해 주세요.")
        @Valid
        Invite invite,

        // 아래 세 값은 아직 연결하지 않는다. 값이 오면 서비스가 TUTORING400_4로 거절한다.
        Long draftId,
        Long consultStudentId,
        Long curriculumId
) {

    private static final String PHONE_REGEX = "^010-?\\d{4}-?\\d{4}$";

    // skipped가 false일 때 parentUpdateAnswer 필수 여부는 서비스에서 검사한다.
    public record Preset(
            @NotNull(message = "운영 방식 건너뜀 여부를 입력해 주세요.")
            Boolean skipped,

            ParentUpdateFrequency parentUpdateAnswer
    ) {
    }

    public record Student(
            @NotNull(message = "학생 이름을 입력해 주세요.")
            @Size(min = 1, max = 10, message = "학생 이름은 1~10자로 입력해 주세요.")
            String name,

            @NotNull(message = "학년을 선택해 주세요.")
            @Min(value = 0, message = "학년을 확인해 주세요.")
            @Max(value = 12, message = "학년을 확인해 주세요.")
            Integer gradeLevel,

            // gradeLevel이 0일 때 필수인지는 서비스에서 검사한다.
            @Size(max = 10, message = "학년은 10자 이내로 입력해 주세요.")
            String gradeOther,

            @NotEmpty(message = "과목을 하나 이상 선택해 주세요.")
            List<@NotNull(message = "과목을 선택해 주세요.") Subject> subjects,

            @Size(max = 10, message = "과목은 10자 이내로 입력해 주세요.")
            String subjectOther
    ) {
    }

    public record TextbookItem(
            @NotNull(message = "교재 이름을 입력해 주세요.")
            @Size(min = 1, max = 20, message = "교재 이름은 1~20자로 입력해 주세요.")
            String name,

            @NotNull(message = "진도 단위를 선택해 주세요.")
            ProgressUnit progressUnit,

            @Size(max = 5, message = "단위 이름은 5자 이내로 입력해 주세요.")
            String customUnitLabel,

            // 없으면 서비스가 0으로 저장한다.
            @Min(value = 0, message = "시작 진도는 0 이상이어야 합니다.")
            Integer startPosition
    ) {
    }

    // 10분 단위 · 시작 < 종료 검사는 서비스에서 한다.
    public record ScheduleItem(
            @NotNull(message = "요일을 선택해 주세요.")
            @Min(value = 1, message = "요일을 확인해 주세요.")
            @Max(value = 7, message = "요일을 확인해 주세요.")
            Integer dayOfWeek,

            @NotNull(message = "시작 시간을 입력해 주세요.")
            LocalTime startTime,

            @NotNull(message = "종료 시간을 입력해 주세요.")
            LocalTime endTime
    ) {
    }

    public record Policy(
            @NotNull(message = "정산 회차를 입력해 주세요.")
            @Min(value = 1, message = "정산 회차는 1~20회로 입력해 주세요.")
            @Max(value = 20, message = "정산 회차는 1~20회로 입력해 주세요.")
            Integer settlementSessionCount,

            @NotNull(message = "결제 시점을 선택해 주세요.")
            PaymentTiming paymentTiming,

            @NotNull(message = "수업료를 입력해 주세요.")
            @Min(value = 10_000, message = "수업료는 10,000원 이상이어야 합니다.")
            @Max(value = 10_000_000, message = "수업료는 10,000,000원 이하여야 합니다.")
            Integer tuitionFee
    ) {
    }

    public record Account(
            @NotNull(message = "은행을 입력해 주세요.")
            @Size(min = 1, max = 20, message = "은행 이름은 1~20자로 입력해 주세요.")
            String bankName,

            // 숫자와 하이픈만, 숫자는 10~14자리.
            @NotNull(message = "계좌번호를 입력해 주세요.")
            @Pattern(regexp = "^(?=(?:\\D*\\d){10,14}\\D*$)[0-9-]+$", message = "계좌번호를 확인해 주세요.")
            String accountNumber,

            @NotNull(message = "예금주를 입력해 주세요.")
            @Size(min = 1, max = 20, message = "예금주는 1~20자로 입력해 주세요.")
            String accountHolder
    ) {
    }

    public record Settings(
            @NotNull(message = "학부모 알림 주기를 선택해 주세요.")
            ParentUpdateFrequency parentUpdateFrequency,

            @NotNull(message = "숙제 알림 여부를 입력해 주세요.")
            Boolean homeworkNotifyEnabled,

            // 없으면 false로 저장한다.
            Boolean unpaidReminderEnabled
    ) {
    }

    public record Invite(
            @NotNull(message = "학부모 정보를 입력해 주세요.")
            @Size(min = 1, max = 2, message = "학부모는 1~2명까지 초대할 수 있습니다.")
            List<@NotNull(message = "학부모 정보를 입력해 주세요.") @Valid ParentItem> parents,

            @Pattern(regexp = PHONE_REGEX, message = "학생 번호를 확인해 주세요.")
            String studentPhone
    ) {

        // 학생 번호는 선택이다. 없으면 빈 Optional.
        public Optional<String> normalizedStudentPhone() {
            return Optional.ofNullable(studentPhone).map(TutoringCreateRequest::stripHyphen);
        }
    }

    public record ParentItem(
            @NotNull(message = "학부모 번호를 입력해 주세요.")
            @Pattern(regexp = PHONE_REGEX, message = "학부모 번호를 확인해 주세요.")
            String phone,

            // 없으면 GUARDIAN으로 저장한다.
            ParticipantLabel label
    ) {

        public String normalizedPhone() {
            return stripHyphen(phone);
        }
    }

    private static String stripHyphen(String phone) {
        return phone.replace("-", "");
    }
}
