package com.sudam.domain.record.service;

import com.sudam.domain.cycle.enums.CycleStatus;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.record.dto.response.RecordFormResponse;
import com.sudam.domain.record.dto.response.RecordFormResponse.TextbookItem;
import com.sudam.domain.record.exception.RecordException;
import com.sudam.domain.record.exception.code.RecordErrorCode;
import com.sudam.domain.record.repository.SessionProgressRepository;
import com.sudam.domain.record.repository.SessionRecordRepository;
import com.sudam.domain.record.repository.TextbookLastPosition;
import com.sudam.domain.session.service.LessonCalendar;
import com.sudam.domain.session.service.LessonRef;
import com.sudam.domain.session.service.LessonSlot;
import com.sudam.domain.session.service.SlotCycle;
import com.sudam.domain.tutoring.entity.Textbook;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.repository.TextbookRepository;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import com.sudam.domain.tutoring.repository.TutoringScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 회차 기록 화면 조회.
 * 기록할 회차를 예정 회차 계산기로 찾고, 사이클 순번 · 교재별 지난 진도 · 지난 숙제를 모은다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordFormService {

    private final TutoringRepository tutoringRepository;
    private final TutoringScheduleRepository tutoringScheduleRepository;
    private final LessonCalendar lessonCalendar;
    private final CycleRepository cycleRepository;
    private final TextbookRepository textbookRepository;
    private final SessionProgressRepository sessionProgressRepository;
    private final SessionRecordRepository sessionRecordRepository;
    private final Clock clock;

    /***
     * 함수 기능: 저장 전 반복 일정 회차의 기록 화면 정보를 조회한다.
     * @param teacherId 로그인한 선생님 (= users.id)
     * @param tutoringId 과외 ID
     * @param scheduleId 반복 일정 ID
     * @param originalDate 반복 일정상 원래 날짜
     * @return 기록 화면 정보
     * @throws RecordException 과외 · 일정이 없으면 RECORD404_1 · RECORD404_2, 수업 날짜가 아니면 RECORD400_1,
     *                         이미 저장된 회차면 RECORD409_1, 진행 중 사이클이 없으면 RECORD409_2
     */
    public RecordFormResponse getRecordForm(Long teacherId, Long tutoringId, Long scheduleId, LocalDate originalDate) {

        // 1. 과외 조회: 없거나 삭제됐거나 다른 선생님의 과외면 RECORD404_1.
        Tutoring tutoring = tutoringRepository.findById(tutoringId)
                .filter(found -> !found.isDeleted() && found.getTeacher().getId().equals(teacherId))
                .orElseThrow(() -> new RecordException(RecordErrorCode.TUTORING_NOT_FOUND));

        // 2. 일정 확인: 없거나 이 과외의 일정이 아니면 RECORD404_2.
        boolean ownSchedule = tutoringScheduleRepository.findById(scheduleId)
                .filter(schedule -> schedule.getTutoring().getId().equals(tutoringId))
                .isPresent();
        if (!ownSchedule) {
            throw new RecordException(RecordErrorCode.SCHEDULE_NOT_FOUND);
        }

        // 3. 회차 찾기: 요일 · 일정 적용 기간 · 과외 기간이 맞지 않으면 RECORD400_1.
        LessonSlot lesson = lessonCalendar.findLesson(tutoring, LessonRef.ofSchedule(scheduleId, originalDate))
                .orElseThrow(() -> new RecordException(RecordErrorCode.SESSION_DATE_NOT_IN_SCHEDULE));

        // 4. 중복 검사: 같은 일정 · 날짜로 저장된 행(기록 · 휴강 · 시간 변경)이 있으면 RECORD409_1.
        //    저장 회차는 sessionId로 여는 2단계 범위다. 이 검사가 응답 생성보다 먼저라 순번(noInCycle)은 항상 값이 있다.
        if (lesson.isStored()) {
            throw new RecordException(RecordErrorCode.ALREADY_RECORDED);
        }

        // 5. 사이클 확인: 진행 중 사이클이 없으면(수업 종료 등) 저장할 수 없으므로 RECORD409_2.
        if (cycleRepository.findByTutoringIdAndStatus(tutoringId, CycleStatus.IN_PROGRESS).isEmpty()) {
            throw new RecordException(RecordErrorCode.NO_IN_PROGRESS_CYCLE);
        }

        // 6. 사이클 순번: 일정 탭과 같은 계산기로 구한다. 오늘 이후 회차는 예측으로 계산되므로
        //    예측 범위(until)가 그 날짜를 포함하도록, 지난 날짜면 오늘 · 오늘 이후면 그 날짜까지로 잡는다.
        LocalDate today = LocalDate.now(clock);
        LocalDate until = originalDate.isBefore(today) ? today : originalDate;
        SlotCycle cycle = lessonCalendar.timelines(List.of(tutoring), until).get(tutoringId).cycleOf(lesson);

        // 7. 교재 · 지난 숙제를 모아 응답을 만든다.
        return RecordFormResponse.of(lesson, tutoring.getStudentName(), cycle,
                textbooksOf(tutoringId, originalDate), previousHomeworkOf(tutoringId, originalDate));
    }

    /***
     * 함수 기능: 과외 교재마다 이 날짜보다 앞선 기록의 마지막 끝 진도를 붙인다.
     * @param tutoringId 과외 ID
     * @param date 기록할 회차 날짜
     * @return 교재 ID 순 교재 항목. 앞선 기록이 없는 교재는 교재의 시작 진도(textbooks.start_position)
     */
    private List<TextbookItem> textbooksOf(Long tutoringId, LocalDate date) {

        // 최근 회차부터 정렬되어 오므로 교재마다 처음 나온 값이 그 교재의 지난 끝 진도다.
        Map<Long, Integer> lastPositions = new HashMap<>();
        for (TextbookLastPosition row : sessionProgressRepository.findPositionsBefore(tutoringId, date)) {
            lastPositions.putIfAbsent(row.textbookId(), row.endPosition());
        }

        // 과외의 교재 전체를 ID 순으로 읽는다. 기록이 한 번도 없는 교재도 화면에 나와야 하므로
        // 진도 행이 아니라 교재 목록을 기준으로 응답을 만든다.
        List<Textbook> textbooks = textbookRepository.findAllByTutoringIdOrderById(tutoringId);
        return textbooks.stream()
                // 앞선 기록이 있는 교재는 그 끝 진도를, 없는 교재(첫 기록 · 새로 추가한 교재)는
                // 과외를 만들 때 입력한 시작 진도(기본 0)를 지난 진도로 쓴다. 화면은 이 값 + 1부터 진도를 제안한다.
                .map(textbook -> TextbookItem.of(textbook,
                        lastPositions.getOrDefault(textbook.getId(), textbook.getStartPosition())))
                .toList();
    }

    /***
     * 함수 기능: 이 날짜보다 앞선 직전 기록의 다음 숙제를 구한다.
     *          직전 기록에 숙제가 비어 있으면 '지난 숙제 없음'이라 더 이전 기록으로 거슬러 가지 않는다.
     * @param tutoringId 과외 ID
     * @param date 기록할 회차 날짜
     * @return 직전 기록의 다음 숙제. 앞선 기록이 없거나 숙제가 비었으면 null
     */
    private String previousHomeworkOf(Long tutoringId, LocalDate date) {
        return sessionRecordRepository.findPreviousHomework(tutoringId, date).orElse(null);
    }
}
