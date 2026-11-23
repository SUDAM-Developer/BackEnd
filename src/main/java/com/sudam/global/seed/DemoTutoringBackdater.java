package com.sudam.global.seed;

import com.sudam.domain.tutoring.entity.Tutoring;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 시드 과외의 생성 시각을 첫 수업 일정 시작일로 당긴다. 실제 과외는 만든 뒤에야 일정 · 기록이 생기므로 그 순서를 맞춘다.
 * 예정 회차 계산기는 과외 생성일 이전 날짜의 예정 회차를 만들지 않아서, 생성 시각이 오늘이면
 * 정산 뒤 아직 기록하지 않은 지난 회차가 일정 탭에서 사라지고 그 날짜를 기록할 수도 없다.
 * created_at은 감사 컬럼(updatable = false)이라 도메인 엔티티에 변경 메서드를 두지 않고 시드 전용 JPQL로 덮어쓴다.
 * 시드 생성과 같은 트랜잭션에서만 돈다 (MANDATORY).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "sudam.seed", name = "enabled", havingValue = "true")
@Transactional(propagation = Propagation.MANDATORY)
public class DemoTutoringBackdater {

    private final EntityManager entityManager;

    /***
     * 함수 기능: 방금 저장한 과외의 생성 시각을 바꾸고, 영속성 컨텍스트의 엔티티도 새 값으로 다시 읽는다.
     * @param tutoring 방금 저장한 과외
     * @param createdAt 당길 생성 시각
     */
    public void backdate(Tutoring tutoring, LocalDateTime createdAt) {
        entityManager.createQuery("update Tutoring t set t.createdAt = :createdAt where t.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", tutoring.getId())
                .executeUpdate();
        entityManager.refresh(tutoring);
    }
}
