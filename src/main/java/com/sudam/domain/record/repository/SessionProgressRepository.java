package com.sudam.domain.record.repository;

import com.sudam.domain.record.entity.SessionProgress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionProgressRepository extends JpaRepository<SessionProgress, Long> {
}
