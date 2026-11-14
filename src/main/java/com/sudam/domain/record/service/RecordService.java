package com.sudam.domain.record.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.enums.CycleStatus;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.record.dto.request.SaveRecordRequest;
import com.sudam.domain.record.dto.request.SaveRecordRequest.ProgressRequest;
import com.sudam.domain.record.dto.response.SaveRecordResponse;
import com.sudam.domain.record.entity.SessionProgress;
import com.sudam.domain.record.entity.SessionRecord;
import com.sudam.domain.record.exception.RecordException;
import com.sudam.domain.record.exception.code.RecordErrorCode;
import com.sudam.domain.record.repository.SessionProgressRepository;
import com.sudam.domain.record.repository.SessionRecordRepository;
import com.sudam.domain.session.entity.Session;
import com.sudam.domain.session.repository.SessionRepository;
import com.sudam.domain.settlement.dto.SettlementResult;
import com.sudam.domain.settlement.service.SettlementService;
import com.sudam.domain.tutoring.entity.Textbook;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicies;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import com.sudam.domain.tutoring.repository.TextbookRepository;
import com.sudam.domain.tutoring.repository.TutoringPolicyRepository;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import com.sudam.domain.tutoring.repository.TutoringScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 수업 기록 서비스. 반복 일정의 수업 한 회차를 기록하면 회차 · 기록 · 진도 행을 만들고,
 * 정산 회차를 채웠으면 같은 트랜잭션에서 정산까지 실행한 뒤 사이클 진행 현황과 정산 결과를 돌려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordService {

    private final TutoringRepository tutoringRepository;
    private final TutoringScheduleRepository tutoringScheduleRepository;
    private final TextbookRepository textbookRepository;
    private final TutoringPolicyRepository tutoringPolicyRepository;
    private final CycleRepository cycleRepository;
    private final SessionRepository sessionRepository;
    private final SessionRecordRepository sessionRecordRepository;
    private final SessionProgressRepository sessionProgressRepository;
    private final SettlementService settlementService;

    /***
     * 함수 기능: 반복 일정의 수업 한 회차를 기록한다. 회차 · 기록 · 진도를 저장하고,
     *          인정 회차가 정산 회차에 닿으면 같은 트랜잭션에서 정산(사이클 교체 · 리포트 · 청구)한다.
     *          정산 중 실패하면 기록 저장까지 전부 롤백된다.
     * @param teacherId 로그인한 선생님 (= users.id)
     * @param tutoringId 과외 ID
     * @param request 기록 요청
     * @return 회차 ID, 사이클 안 번호, 사이클 진행 현황, 정산 결과
     * @throws RecordException 과외 · 일정 · 교재가 없거나, 날짜 · 진도가 맞지 않거나, 이미 기록했거나, 진행 중 사이클이 없는 경우
     */
    @Transactional
    public SaveRecordResponse save(Long teacherId, Long tutoringId, SaveRecordRequest request) {
        // 1. 과외 조회: 과외를 가져온다. 없거나 삭제됐거나 다른 선생님의 과외면 RECORD404_1.
        Tutoring tutoring = tutoringRepository.findById(tutoringId)
                .filter(found -> !found.isDeleted() && found.getTeacher().getId().equals(teacherId))
                .orElseThrow(() -> new RecordException(RecordErrorCode.TUTORING_NOT_FOUND));

        // 2. 일정 조회: 요청한 반복 일정을 가져온다. 없거나 이 과외의 일정이 아니면 RECORD404_2.
        TutoringSchedule schedule = tutoringScheduleRepository.findById(request.scheduleId())
                .filter(found -> found.getTutoring().getId().equals(tutoringId))
                .orElseThrow(() -> new RecordException(RecordErrorCode.SCHEDULE_NOT_FOUND));

        // 3. 날짜 검사: 미래 날짜인지, 일정의 요일 · 적용 기간에 맞는지 확인한다.
        LocalDate date = request.originalDate();
        validateDate(schedule, date);

        // 4. 진도 검사: 진도 범위와 교재 소속을 확인하고, 교재를 ID로 찾을 수 있는 Map으로 받는다.
        Map<Long, Textbook> textbooks = findTextbooks(tutoringId, request.progress());

        // 5. 중복 검사: 같은 일정 · 같은 날짜의 회차가 이미 있으면 RECORD409_1.
        if (sessionRepository.existsByScheduleIdAndOriginalDate(schedule.getId(), date)) {
            throw new RecordException(RecordErrorCode.ALREADY_RECORDED);
        }

        // 6. 사이클 조회: 과외의 진행 중 사이클을 가져온다. 없으면 RECORD409_2.
        Cycle cycle = cycleRepository.findByTutoringIdAndStatus(tutoringId, CycleStatus.IN_PROGRESS)
                .orElseThrow(() -> new RecordException(RecordErrorCode.NO_IN_PROGRESS_CYCLE));

        // 7. 회차 저장: 정규 · 완료 · 인정 회차로 sessions 행을 만든다.
        Session session = sessionRepository.save(Session.completeRegular(tutoring, cycle, schedule, date));

        // 8. 기록 저장: 이해도 · 숙제 · 관찰 메모를 session_records 행 1개로 저장한다.
        sessionRecordRepository.save(SessionRecord.create(session, request.understanding(),
                request.previousHomeworkResult(), request.nextHomework(), request.observationMemo()));

        // 9. 진도 저장: 요청한 교재마다 session_progress 행을 만들어 한 번에 저장한다.
        sessionProgressRepository.saveAll(request.progress().stream()
                .map(progress -> SessionProgress.create(session, textbooks.get(progress.textbookId()),
                        progress.startPosition(), progress.endPosition()))
                .toList());

        // 10. 응답 계산: 진행 중 사이클의 인정 회차를 정렬해 가져온다.
        //     목록 크기 = 인정 회차 수, 방금 저장한 회차의 위치 + 1 = 사이클 안 번호.
        //     정산이 사이클을 닫기 전에 계산해, 정산했더라도 방금 닫은 사이클 기준의 현황을 돌려준다.
        List<Session> recognized = sessionRepository.findRecognizedInCycle(cycle.getId());
        int noInCycle = recognized.indexOf(session) + 1;
        int settlementSessionCount = settlementSessionCount(tutoringId, cycle.getCycleNo());

        // 11. 정산: 인정 회차가 정산 회차에 닿았으면 정산한다. 닿지 않았으면 settled = false가 돌아온다.
        //     같은 트랜잭션이라 정산 쪽 조회에도 방금 저장한 회차가 보인다.
        SettlementResult settlement = settlementService.settleIfReached(tutoringId);

        return SaveRecordResponse.of(session.getId(), noInCycle, cycle.getCycleNo(), recognized.size(),
                settlementSessionCount, settlement);
    }

    /***
     * 함수 기능: 기록할 날짜가 반복 일정의 수업 날짜인지 확인한다.
     * @param schedule 회차가 속한 반복 일정
     * @param date 반복 일정상 수업 날짜
     * @throws RecordException 미래 날짜면 RECORD400_2, 요일이나 적용 기간이 맞지 않으면 RECORD400_1
     */
    private void validateDate(TutoringSchedule schedule, LocalDate date) {
        // 오늘보다 뒤의 날짜면 아직 하지 않은 수업이다. 오늘 날짜는 통과한다.
        if (date.isAfter(LocalDate.now())) {
            throw new RecordException(RecordErrorCode.FUTURE_SESSION_DATE);
        }
        // 날짜의 요일(1=월 ~ 7=일)이 일정의 요일과 같은지 확인한다.
        boolean sameDayOfWeek = date.getDayOfWeek().getValue() == schedule.getDayOfWeek();
        // 날짜가 일정 적용 기간 안인지 확인한다. 적용 종료일이 없으면 현재 적용 중인 일정이다.
        boolean inPeriod = !date.isBefore(schedule.getEffectiveFrom())
                && (schedule.getEffectiveTo() == null || !date.isAfter(schedule.getEffectiveTo()));
        if (!sameDayOfWeek || !inPeriod) {
            throw new RecordException(RecordErrorCode.SESSION_DATE_NOT_IN_SCHEDULE);
        }
    }

    /***
     * 함수 기능: 진도 범위와 교재 소속을 확인하고, 진도 행을 만들 때 쓰도록 교재를 ID로 묶어 돌려준다.
     * @param tutoringId 과외 ID
     * @param progress 요청한 교재별 진도
     * @return 교재 ID → 교재
     * @throws RecordException 시작 진도가 끝 진도보다 크면 RECORD400_3, 없거나 이 과외의 교재가 아니면 RECORD404_3
     */
    private Map<Long, Textbook> findTextbooks(Long tutoringId, List<ProgressRequest> progress) {
        // 교재마다 시작 진도가 끝 진도 이하인지 확인한다.
        for (ProgressRequest item : progress) {
            if (item.startPosition() > item.endPosition()) {
                throw new RecordException(RecordErrorCode.INVALID_PROGRESS_RANGE);
            }
        }
        // 요청한 교재를 한 번에 조회하고, 이 과외의 교재만 남겨 ID → 교재 Map으로 만든다.
        List<Long> textbookIds = progress.stream().map(ProgressRequest::textbookId).toList();
        Map<Long, Textbook> textbooks = textbookRepository.findAllById(textbookIds).stream()
                .filter(textbook -> textbook.getTutoring().getId().equals(tutoringId))
                .collect(Collectors.toMap(Textbook::getId, Function.identity()));
        // 남은 교재 수가 요청 수보다 적으면 없는 교재나 다른 과외의 교재가 섞여 있다.
        if (textbooks.size() != textbookIds.size()) {
            throw new RecordException(RecordErrorCode.TEXTBOOK_NOT_FOUND);
        }
        return textbooks;
    }

    /***
     * 함수 기능: 사이클에 적용되는 과외 정책을 골라 정산 회차를 돌려준다.
     * @param tutoringId 과외 ID
     * @param cycleNo 사이클 번호
     * @return 정산 회차 (정산까지 채워야 하는 인정 회차 수)
     * @throws IllegalStateException 적용할 정책이 없는 경우. 과외를 만들 때 정책을 함께 저장하므로 데이터 이상이다
     */
    private int settlementSessionCount(Long tutoringId, int cycleNo) {
        return TutoringPolicies.applicableTo(tutoringPolicyRepository.findAllByTutoringId(tutoringId), cycleNo)
                .map(TutoringPolicy::getSettlementSessionCnt)
                .orElseThrow(() -> new IllegalStateException("적용할 과외 정책이 없습니다. tutoringId=" + tutoringId));
    }
}
