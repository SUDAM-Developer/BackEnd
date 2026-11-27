package com.sudam.domain.session.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;
import com.sudam.domain.session.service.LessonSlot;
import com.sudam.domain.session.service.SlotCycle;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import com.sudam.domain.user.enums.Subject;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 홈 화면 오늘 수업 목록 응답.
 */
public record TodaySessionsResponse(
        LocalDate date, // 오늘 날짜 (서버 기준)
        List<Lesson> sessions // 오늘 수업, 시작 시각순. 없으면 빈 배열
) {

    /**
     * 오늘 수업 카드 하나. 저장 전 회차는 (scheduleId, originalDate), 저장된 회차는 sessionId로 기록 · 휴강 API에 보낸다.
     */
    @Builder
    public record Lesson(
            Long sessionId, // 저장된 회차면 값, 계산된 예정 회차면 null
            Long scheduleId, // 반복 일정 회차면 값, 보강 · 추가 회차면 null
            LocalDate originalDate, // 반복 일정상 원래 날짜. 보강 · 추가 회차면 null
            Long tutoringId, // 과외 ID
            String studentName, // 학생 이름
            Integer gradeLevel, // 현재 학년 (1~12 = 초1~고3, 0 = 기타)
            String gradeOther, // 학년 '기타' 직접 입력값
            List<LessonSubject> subjects, // 과외 과목, 과목 enum 순서
            SessionType sessionType, // REGULAR · MAKEUP · EXTRA
            SessionStatus status, // SCHEDULED · COMPLETED · CANCELED
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime, // 시작 시각
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime, // 종료 시각
            @JsonProperty("isTimeChanged") boolean isTimeChanged, // 시간을 바꾼 회차
            List<RegularSchedule> regularSchedules, // 과외의 지금 적용 중인 정규 일정, 요일 → 시작 시각순
            Cycle cycle // 사이클 안 번호 · 인정 수 · 정산 회차
    ) {

        /***
         * 함수 기능: 계산기 회차 하나와 그 과외 정보를 오늘 수업 카드로 만든다.
         * @param slot 오늘 회차
         * @param tutoring 회차의 과외
         * @param cycle 회차의 사이클 정보 (CycleTimeline#cycleOf)
         * @param regularSchedules 과외의 지금 적용 중인 정규 일정 (정렬된 것)
         * @param today 오늘. 현재 학년 계산에 쓴다
         * @return 오늘 수업 카드
         */
        public static Lesson of(LessonSlot slot, Tutoring tutoring, SlotCycle cycle,
                                List<RegularSchedule> regularSchedules, LocalDate today) {
            List<LessonSubject> subjects = tutoring.sortedSubjects().stream()
                    .map(subject -> LessonSubject.of(subject, tutoring.getSubjectOther()))
                    .toList();
            return Lesson.builder()
                    .sessionId(slot.sessionId())
                    .scheduleId(slot.scheduleId())
                    .originalDate(slot.originalDate())
                    .tutoringId(tutoring.getId())
                    .studentName(tutoring.getStudentName())
                    .gradeLevel(tutoring.currentGradeLevel(today))
                    .gradeOther(tutoring.getGradeOther())
                    .subjects(subjects)
                    .sessionType(slot.sessionType())
                    .status(slot.status())
                    .startTime(slot.startTime())
                    .endTime(slot.endTime())
                    .isTimeChanged(slot.timeChanged())
                    .regularSchedules(regularSchedules)
                    .cycle(Cycle.from(cycle))
                    .build();
        }
    }

    /**
     * 과외 과목 하나.
     */
    public record LessonSubject(
            Subject subjectCode, // 과목 코드
            String subjectName // 과목 이름. OTHER는 직접 입력한 과목명 (없으면 '기타')
    ) {

        public static LessonSubject of(Subject subject, String subjectOther) {
            String name = subject == Subject.OTHER && subjectOther != null ? subjectOther : subject.getName();
            return new LessonSubject(subject, name);
        }
    }

    /**
     * 정규 일정 하나.
     */
    public record RegularSchedule(
            int dayOfWeek, // 1(월)~7(일)
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime, // 시작 시각
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime // 종료 시각
    ) {

        public static RegularSchedule from(TutoringSchedule schedule) {
            return new RegularSchedule(schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime());
        }
    }

    /**
     * 회차의 사이클 정보. 규칙은 daily API의 cycle과 같다.
     */
    public record Cycle(
            Integer sessionNo, // 사이클 안 번호 (daily noInCycle). 미기록은 지금 기록하면 될 번호, 미인정 휴강은 null
            int recordedCount, // 그 사이클의 인정 회차 수 (daily recognizedCount)
            int settlementSessionCount // 그 사이클의 정산 회차
    ) {

        public static Cycle from(SlotCycle cycle) {
            return new Cycle(cycle.noInCycle(), cycle.recognizedCount(), cycle.settlementSessionCount());
        }
    }
}
