package com.sudam.domain.cycle.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.exception.CycleException;
import com.sudam.domain.cycle.exception.code.CycleErrorCode;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.tutoring.entity.Tutoring;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 과외의 다음 진행 중 사이클을 여는 창구.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CycleOpener {

    private final CycleRepository cycleRepository;

    /***
     * 함수 기능: 과외의 다음 사이클을 IN_PROGRESS로 연다. 사이클이 없으면 1번, 마지막이 정산 완료면 그 번호 + 1.
     * @param tutoring  잠근 과외
     * @param startedAt 사이클이 열린 시각 (정산은 닫은 시각과 같은 값을 넘긴다)
     * @return 새로 연 사이클
     * @throws CycleException 진행 중 사이클이 이미 있으면 CYCLE409_2
     */
    public Cycle openNext(Tutoring tutoring, LocalDateTime startedAt) {

        Optional<Cycle> last = cycleRepository.findTopByTutoringIdOrderByCycleNoDesc(tutoring.getId());

        // 현재 과외의 사이클이 존재하는데 InProgress(진행 중)이라면,
        // 과외 사이클을 새로 만들지 않는다. (과외 1개당 진행 중인 사이클은 오직 1개이다.)
        if (last.isPresent() && last.get().isInProgress()) {
            throw new CycleException(CycleErrorCode.IN_PROGRESS_EXISTS);
        }

        // 사이클이 없으면 1번, 마지막이 정산 완료(사이클이 존재)면 그 번호 + 1.
        int nextNo = last.map(cycle -> cycle.getCycleNo() + 1).orElse(1);

        return cycleRepository.save(Cycle.open(tutoring, nextNo, startedAt));
    }
}
