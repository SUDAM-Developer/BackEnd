package com.sudam.domain.cycle.repository;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.enums.CycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 사이클을 만들거나 상태를 바꾸는 쪽은 먼저 {@code TutoringRepository.findByIdForUpdate}로 과외 행을 잠근다.
 * 잠금 없이 아래 조회 결과로 새 사이클을 열면 '진행 중 사이클은 과외당 1개'가 깨질 수 있다.
 */
public interface CycleRepository extends JpaRepository<Cycle, Long> {

    // 진행 중 사이클 조회는 status = IN_PROGRESS로 부른다. 과외당 최대 1개다.
    Optional<Cycle> findByTutoringIdAndStatus(Long tutoringId, CycleStatus status);

    Optional<Cycle> findByTutoringIdAndCycleNo(Long tutoringId, Integer cycleNo);

    List<Cycle> findAllByTutoringIdOrderByCycleNo(Long tutoringId);
}
