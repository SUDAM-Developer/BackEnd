package com.sudam.domain.tutoring.repository;

import com.sudam.domain.tutoring.entity.Tutoring;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TutoringRepository extends JpaRepository<Tutoring, Long> {

    // 사이클 잠금 기준 (SELECT … FOR UPDATE). 사이클을 열고 닫는 모든 쪽 — 과외 만들기 · 정산 실행 · 수업 종료 · 수업료 변경 —
    // 은 같은 트랜잭션에서 이 메서드로 과외 행을 먼저 잠근 뒤 진행 중 사이클을 조회 · 변경한다.
    // in_progress_key 생성 열이 없어 '진행 중 사이클은 과외당 1개'를 이 잠금으로 보장한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tutoring t where t.id = :id")
    Optional<Tutoring> findByIdForUpdate(@Param("id") Long id);

    // 선생님에게 과외가 하나라도 있는지 (삭제 · 종료 포함). 시연용 가상 데이터 실행기가 이미 만들었는지 판단할 때 쓴다.
    boolean existsByTeacherId(Long teacherId);
}
