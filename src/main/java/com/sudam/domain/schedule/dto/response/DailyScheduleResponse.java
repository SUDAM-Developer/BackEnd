package com.sudam.domain.schedule.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sudam.domain.schedule.enums.LessonAction;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;
import com.sudam.domain.session.service.LessonSlot;
import com.sudam.domain.session.service.SlotCycle;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 그날 일정 · 수업 응답 (5.1.1). 순서는 화면과 같다: 선생님 일정 → 그날 수업(시각순) → 오늘이면 다가오는 수업 3건.
 * 홈 화면 오늘 수업도 date 없이 불러 sessions만 쓴다.
 */
public record DailyScheduleResponse(
        LocalDate date,                           // 고른 날짜
        boolean isToday,                          // 목록 제목 '· 오늘' · 다가오는 수업 표시 여부
        List<Object> events,                      // 그날 선생님 일정. teacher_events(PR-9) 전까지 빈 배열
        List<Lesson> sessions,                    // 그날 수업. 없으면 빈 배열 → '수업이 없는 날이에요'
        List<Lesson> upcoming                     // 오늘일 때만 지금 이후 수업 최대 3건 (8주 안, 휴강 포함). 오늘이 아니면 빈 배열
) {

    /**
     * 회차 카드 하나. 저장 전 회차는 (scheduleId, originalDate), 저장된 회차는 sessionId로 기록 · 휴강 · 시간 바꾸기 API에 보낸다.
     */
    @Schema(name = "DailyScheduleLesson")
    @Builder
    public record Lesson(
            Long sessionId,                         // 저장된 회차면 값, 계산된 예정 회차면 null
            Long scheduleId,                        // 반복 일정 회차면 값, 보강 · 추가 회차면 null
            LocalDate originalDate,                 // 반복 일정상 원래 날짜
            LocalDate date,                         // 실제 수업 날짜 (시간을 바꾼 회차는 바뀐 날짜)
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
            LocalTime startTime,                    // 시작 시각 (HH:mm)
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
            LocalTime endTime,                      // 종료 시각 (HH:mm)
            Long tutoringId,                        // 과외 ID (점 색)
            String studentName,                     // 학생 이름
            String subjectName,                     // 과목 표시 이름 ('영어 · 수학')
            SessionType sessionType,                // REGULAR · MAKEUP · EXTRA
            SessionStatus status,                   // SCHEDULED · COMPLETED · CANCELED
            String cancelReason,                    // 휴강 사유. 휴강 사유 컬럼이 생기기 전까지 null
            boolean hasRecord,                      // 수업 기록이 있으면 true ('기록됨', [기록완료])
            boolean timeChanged,                    // 시간을 바꾼 회차
            boolean pendingTimeChange,              // 시간 바꾸기를 요청했지만 아직 카톡을 안 보냄. 시간 변경(PR-10) 전까지 false
            Object makeup,                          // 휴강 회차에 걸린 보강 요청. 보강 기능 전까지 null
            @Schema(description = "카드 날짜 기준 현재 학년 (1~12 = 초1~고3, 0 = 기타). 3월마다 하나씩 오르고 고3을 넘으면 12",
                    example = "8")
            int gradeLevel,                         // 카드 날짜 기준 현재 학년 (1~12 = 초1~고3, 0 = 기타)
            @Schema(description = "학년 '기타' 직접 입력값. 기타가 아니면 null", example = "재수생", nullable = true)
            String gradeOther,                      // 학년 '기타' 직접 입력값
            @Schema(description = "카드 날짜에 적용 중인 과외의 정규 일정, 요일 → 시작 시각순. 홈 카드의 '매주 월 · 금 18:00'")
            List<RegularSchedule> regularSchedules, // 카드 날짜에 적용 중인 정규 일정, 요일 → 시작 시각순
            Cycle cycle,                            // 'n/N회차' · '새 사이클 n회차'
            LessonAction action                     // 오른쪽 버튼
    ) {

        /***
         * 함수 기능: 계산기 회차 하나와 그 과외 정보를 회차 카드로 만든다. 학년은 카드 날짜 기준으로 계산한다.
         *          cancelReason · pendingTimeChange · makeup은 휴강 사유 컬럼 · 시간 변경(PR-10) · 보강 기능이 생기면 채운다.
         * @param slot             회차
         * @param tutoring         회차의 과외
         * @param cycle            회차의 사이클 정보 (CycleTimeline#cycleOf)
         * @param regularSchedules 카드 날짜에 적용 중인 정규 일정 (정렬된 것)
         * @param hasRecord        수업 기록이 있는지
         * @param action           오른쪽 버튼
         * @return 회차 카드
         */
        public static Lesson of(LessonSlot slot, Tutoring tutoring, SlotCycle cycle,
                                List<RegularSchedule> regularSchedules, boolean hasRecord, LessonAction action) {
            return Lesson.builder()
                    .sessionId(slot.sessionId())
                    .scheduleId(slot.scheduleId())
                    .originalDate(slot.originalDate())
                    .date(slot.date())
                    .startTime(slot.startTime())
                    .endTime(slot.endTime())
                    .tutoringId(tutoring.getId())
                    .studentName(tutoring.getStudentName())
                    .subjectName(tutoring.subjectNames())
                    .sessionType(slot.sessionType())
                    .status(slot.status())
                    .hasRecord(hasRecord)
                    .timeChanged(slot.timeChanged())
                    .gradeLevel(tutoring.currentGradeLevel(slot.date()))
                    .gradeOther(tutoring.getGradeOther())
                    .regularSchedules(regularSchedules)
                    .cycle(Cycle.from(cycle))
                    .action(action)
                    .build();
        }
    }

    /**
     * 과외의 정규 일정 하나 ('매주 월 18:00').
     */
    public record RegularSchedule(
            @Schema(description = "요일 1(월)~7(일)", example = "1")
            int dayOfWeek,       // 1(월)~7(일)
            @Schema(description = "시작 시각 (HH:mm)", type = "string", example = "18:00")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
            LocalTime startTime, // 시작 시각 (HH:mm)
            @Schema(description = "종료 시각 (HH:mm)", type = "string", example = "19:30")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
            LocalTime endTime    // 종료 시각 (HH:mm)
    ) {

        public static RegularSchedule from(TutoringSchedule schedule) {
            return new RegularSchedule(schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime());
        }
    }

    /**
     * 회차의 사이클 정보.
     */
    @Schema(name = "DailyScheduleCycle")
    public record Cycle(
            int cycleNo,                                     // 소속(예측이면 예상) 사이클 번호
            Integer noInCycle,                               // 사이클 안 번호. 미인정 휴강이면 null
            int recognizedCount,                             // 그 사이클의 인정 회차 수
            int settlementSessionCount,                      // 그 사이클의 정산 회차
            boolean startsNewCycle,                          // 예측상 다음 사이클의 첫 회차 ('새 사이클 1회차')
            @JsonProperty("isPredicted") boolean isPredicted // 아직 기록하지 않아 번호가 예측값
    ) {

        public static Cycle from(SlotCycle cycle) {
            return new Cycle(cycle.cycleNo(), cycle.noInCycle(), cycle.recognizedCount(),
                    cycle.settlementSessionCount(), cycle.startsNewCycle(), cycle.isPredicted());
        }
    }
}
