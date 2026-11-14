package com.sudam.domain.user.repository;

import com.sudam.domain.user.entity.Terms;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TermsRepository extends JpaRepository<Terms, Long> {

    // 시행 중인 모든 버전을 가져오고, code별 최신 버전 선택은 서비스에서 한다.
    List<Terms> findByEffectiveAtLessThanEqual(LocalDateTime now);
}
