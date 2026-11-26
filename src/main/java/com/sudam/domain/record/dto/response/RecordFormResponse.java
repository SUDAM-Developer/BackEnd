package com.sudam.domain.record.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sudam.domain.session.enums.SessionType;
import com.sudam.domain.session.service.LessonSlot;
import com.sudam.domain.session.service.SlotCycle;
import com.sudam.domain.tutoring.entity.Textbook;
import com.sudam.domain.tutoring.enums.ProgressUnit;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 회차 기록 화면(T-10) 조회 응답. 기록할 회차와 그 회차의 사이클 순번, 교재별 지난 진도, 지난 숙제를 담는다.
 * 지금은 저장 전 정규 회차만 다루므로 sessionId · record는 항상 null이다 (저장된 회차 · 기록 수정은 2단계).
 */
@Builder
public record RecordFormResponse(

        // 저장된 회차 ID. 저장 전 회차라 지금은 항상 null
        Long sessionId,

        // 반복 일정 ID와 일정상 원래 날짜. 저장 API(POST records)에 그대로 보낸다
        Long scheduleId,
        LocalDate originalDate,

        // 실제 수업 날짜 · 시각. 정규 회차는 원래 날짜와 반복 일정 시각이다
        LocalDate sessionDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime,

        // 정규 · 보강 · 추가. 지금은 항상 REGULAR
        SessionType sessionType,

        String studentName,

        // 지금 기록하면 될 사이클 안 순번 (일정 탭 카드의 'n/N회차'와 같은 계산)
        int noInCycle,

        // 진행 중 사이클 현황
        CycleSummary cycle,

        // 과외 교재. 교재 ID 순
        List<TextbookItem> textbooks,

        // 직전 기록의 다음 숙제. 지난 기록이 없거나 숙제를 적지 않았으면 null
        String previousHomework,

        // 이미 기록한 회차의 기록. 지금은 항상 null (2단계 기록 수정에서 채운다)
        RecordDetail record
) {

    public record CycleSummary(

            // 사이클 번호 (과외 안에서 1부터)
            int cycleNo,

            // 지금까지 이 사이클에 쌓인 인정 회차 수 (이번 회차 제외)
            int recognizedCount,

            // 이 사이클의 정산 회차
            int settlementSessionCount
    ) {
    }

    @Builder
    public record TextbookItem(

            Long textbookId,

            String name,

            // 진도 단위. CUSTOM이면 customUnitLabel을 단위로 쓴다
            ProgressUnit progressUnit,

            String customUnitLabel,

            // 지난 기록의 끝 진도. 기록이 없으면 교재의 시작 진도(textbooks.start_position)
            int lastPosition
    ) {

        /***
         * 함수 기능: 교재와 지난 진도로 교재 항목을 만든다.
         * @param textbook 과외 교재
         * @param lastPosition 지난 기록의 끝 진도 (기록이 없으면 교재의 시작 진도를 넘긴다)
         * @return 교재 항목
         */
        public static TextbookItem of(Textbook textbook, int lastPosition) {
            return TextbookItem.builder()
                    .textbookId(textbook.getId())
                    .name(textbook.getName())
                    .progressUnit(textbook.getProgressUnit())
                    .customUnitLabel(textbook.getCustomUnitLabel())
                    .lastPosition(lastPosition)
                    .build();
        }
    }

    // 기록 수정(2단계)에서 저장된 기록을 담는다. 응답 키("record")를 지금부터 고정하려고 빈 모양으로 둔다.
    public record RecordDetail() {
    }

    /***
     * 함수 기능: 기록 화면 응답을 만든다. 회차 · 사이클 필드는 예정 회차 계산기가 낸 값을 그대로 옮긴다.
     * @param lesson 기록할 회차 (LessonCalendar.findLesson 결과)
     * @param studentName 학생 이름
     * @param cycle 회차의 사이클 정보 (CycleTimeline.cycleOf 결과)
     * @param textbooks 교재별 지난 진도
     * @param previousHomework 직전 기록의 다음 숙제
     * @return 기록 화면 응답
     */
    public static RecordFormResponse of(LessonSlot lesson, String studentName, SlotCycle cycle,
                                           List<TextbookItem> textbooks, String previousHomework) {
        return RecordFormResponse.builder()
                .sessionId(lesson.sessionId())
                .scheduleId(lesson.scheduleId())
                .originalDate(lesson.originalDate())
                .sessionDate(lesson.date())
                .startTime(lesson.startTime())
                .endTime(lesson.endTime())
                .sessionType(lesson.sessionType())
                .studentName(studentName)
                .noInCycle(cycle.noInCycle())
                .cycle(new CycleSummary(cycle.cycleNo(), cycle.recognizedCount(), cycle.settlementSessionCount()))
                .textbooks(List.copyOf(textbooks))
                .previousHomework(previousHomework)
                .record(null) // 2단계 기록 수정 전까지 항상 null. 빠뜨린 것이 아니라 의도한 값임을 드러내려고 적는다
                .build();
    }
}
