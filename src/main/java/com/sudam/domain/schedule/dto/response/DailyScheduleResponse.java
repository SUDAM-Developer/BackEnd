package com.sudam.domain.schedule.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sudam.domain.schedule.enums.LessonAction;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.enums.SessionType;
import com.sudam.domain.session.service.SlotCycle;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 그날 일정 · 수업 응답 (5.1.1). 순서는 화면과 같다: 선생님 일정 → 그날 수업(시각순) → 오늘이면 다가오는 수업 3건.
 *
 * @param date     고른 날짜
 * @param isToday  목록 제목 '· 오늘' · 다가오는 수업 표시 여부
 * @param events   그날 선생님 일정. teacher_events(PR-9) 전까지 빈 배열
 * @param sessions 그날 수업. 없으면 빈 배열 → '수업이 없는 날이에요'
 * @param upcoming 오늘일 때만 지금 이후 수업 최대 3건 (8주 안, 휴강 포함). 오늘이 아니면 빈 배열
 */
public record DailyScheduleResponse(
        LocalDate date,
        @JsonProperty("isToday") boolean isToday,
        List<Object> events,
        List<Lesson> sessions,
        List<Lesson> upcoming
) {

    /**
     * 회차 카드 하나. 저장 전 회차는 (scheduleId, originalDate), 저장된 회차는 sessionId로 기록 · 휴강 · 시간 바꾸기 API에 보낸다.
     *
     * @param sessionId         저장된 회차면 값, 계산된 예정 회차면 null
     * @param scheduleId        반복 일정 회차면 값, 보강 · 추가 회차면 null
     * @param originalDate      반복 일정상 원래 날짜
     * @param date              실제 수업 날짜 (시간을 바꾼 회차는 바뀐 날짜)
     * @param startTime         시작 시각 (HH:mm)
     * @param endTime           종료 시각 (HH:mm)
     * @param tutoringId        과외 ID (점 색)
     * @param studentName       학생 이름
     * @param subjectName       과목 표시 이름 ('영어 · 수학')
     * @param sessionType       REGULAR · MAKEUP · EXTRA
     * @param status            SCHEDULED · COMPLETED · CANCELED
     * @param cancelReason      휴강 사유. 휴강 사유 컬럼이 생기기 전까지 null
     * @param hasRecord         수업 기록이 있으면 true ('기록됨', [기록완료])
     * @param timeChanged       시간을 바꾼 회차
     * @param pendingTimeChange 시간 바꾸기를 요청했지만 아직 카톡을 안 보냄. 시간 변경(PR-10) 전까지 false
     * @param makeup            휴강 회차에 걸린 보강 요청. 보강 기능 전까지 null
     * @param cycle             'n/N회차' · '새 사이클 n회차'
     * @param action            오른쪽 버튼
     */
    public record Lesson(
            Long sessionId,
            Long scheduleId,
            LocalDate originalDate,
            LocalDate date,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime,
            Long tutoringId,
            String studentName,
            String subjectName,
            SessionType sessionType,
            SessionStatus status,
            String cancelReason,
            boolean hasRecord,
            boolean timeChanged,
            boolean pendingTimeChange,
            Object makeup,
            Cycle cycle,
            LessonAction action
    ) {
    }

    /**
     * 회차의 사이클 정보.
     *
     * @param cycleNo                소속(예측이면 예상) 사이클 번호
     * @param noInCycle              사이클 안 번호. 미인정 휴강이면 null
     * @param recognizedCount        그 사이클의 인정 회차 수
     * @param settlementSessionCount 그 사이클의 정산 회차
     * @param startsNewCycle         예측상 다음 사이클의 첫 회차 ('새 사이클 1회차')
     * @param isPredicted            아직 기록하지 않아 번호가 예측값
     */
    public record Cycle(
            int cycleNo,
            Integer noInCycle,
            int recognizedCount,
            int settlementSessionCount,
            boolean startsNewCycle,
            @JsonProperty("isPredicted") boolean isPredicted
    ) {

        public static Cycle from(SlotCycle cycle) {
            return new Cycle(cycle.cycleNo(), cycle.noInCycle(), cycle.recognizedCount(),
                    cycle.settlementSessionCount(), cycle.startsNewCycle(), cycle.isPredicted());
        }
    }
}
