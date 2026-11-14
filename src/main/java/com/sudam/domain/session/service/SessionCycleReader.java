package com.sudam.domain.session.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.invoice.entity.CanceledLesson;
import com.sudam.domain.session.enums.SessionStatus;
import com.sudam.domain.session.repository.SessionRepository;
import com.sudam.domain.settlement.port.CyclePeriod;
import com.sudam.domain.settlement.port.CycleSessionReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 정산 실행(CycleSessionReader 포트)에 사이클의 회차 숫자를 알려주는 구현.
 * 방금 저장한 기록이 보여야 하므로 정산을 부른 기록 저장 트랜잭션에 참여만 한다 (MANDATORY, 새 트랜잭션을 열지 않음).
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class SessionCycleReader implements CycleSessionReader {

    private final SessionRepository sessionRepository;
    private final CycleRepository cycleRepository;

    /***
     * 함수 기능: 사이클의 인정 회차 수를 센다.
     * @param tutoringId 과외 ID (사이클이 과외에 속하므로 조회에는 쓰지 않는다)
     * @param cycleId 사이클 ID
     * @return 인정 회차 수. 없으면 0
     */
    @Override
    public int countRecognized(Long tutoringId, Long cycleId) {
        return Math.toIntExact(sessionRepository.countByCycleIdAndIsRecognizedTrue(cycleId));
    }

    /***
     * 함수 기능: 사이클 기간(첫 인정 회차 날짜 ~ 마지막 인정 회차 날짜)을 구한다.
     * @param tutoringId 과외 ID
     * @param cycleId 사이클 ID
     * @return 사이클 기간
     * @throws IllegalStateException 인정 회차가 없는 경우. 정산은 회차를 채운 뒤에만 부르므로 호출 순서 오류다
     */
    @Override
    public CyclePeriod period(Long tutoringId, Long cycleId) {
        return new CyclePeriod(
                sessionRepository.findFirstRecognizedDate(cycleId).orElseThrow(() -> noRecognized(cycleId)),
                sessionRepository.findLastRecognizedDate(cycleId).orElseThrow(() -> noRecognized(cycleId))
        );
    }

    /***
     * 함수 기능: 사이클 기간 안의 휴강 내역을 날짜순으로 돌려준다.
     *          미인정 휴강은 cycle_id가 NULL이라 날짜로 소속을 정한다:
     *          (직전 사이클의 마지막 인정 날짜, 이 사이클의 마지막 인정 날짜]. 첫 사이클이면 하한이 없다.
     * @param tutoringId 과외 ID
     * @param cycleId 사이클 ID
     * @return 휴강 내역. 없으면 빈 목록
     * @throws IllegalStateException 사이클이나 인정 회차가 없는 경우
     */
    @Override
    public List<CanceledLesson> canceledIn(Long tutoringId, Long cycleId) {
        Cycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new IllegalStateException("사이클이 없습니다. cycleId=" + cycleId));
        LocalDate until = sessionRepository.findLastRecognizedDate(cycleId)
                .orElseThrow(() -> noRecognized(cycleId));

        // 직전 사이클의 마지막 인정 날짜 다음 날부터가 이 사이클 몫이다. 첫 사이클이면 null(하한 없음).
        LocalDate after = cycleRepository.findByTutoringIdAndCycleNo(tutoringId, cycle.getCycleNo() - 1)
                .flatMap(prev -> sessionRepository.findLastRecognizedDate(prev.getId()))
                .orElse(null);

        // 휴강 사유 컬럼(sessions.cancel_reason)은 휴강 기능과 함께 추가된다. 그 전에는 사유 없이 null로 둔다.
        return sessionRepository.findByStatusInDateRange(tutoringId, SessionStatus.CANCELED, after, until).stream()
                .map(session -> new CanceledLesson(session.getSessionDate(), null))
                .toList();
    }

    private IllegalStateException noRecognized(Long cycleId) {
        return new IllegalStateException("사이클에 인정 회차가 없습니다. cycleId=" + cycleId);
    }
}
