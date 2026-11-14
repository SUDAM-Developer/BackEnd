package com.sudam.domain.record.repository;

import com.sudam.domain.record.entity.SessionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRecordRepository extends JpaRepository<SessionRecord, Long> {
}
