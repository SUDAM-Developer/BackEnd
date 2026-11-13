package com.sudam.domain.tutoring.repository;

import com.sudam.domain.tutoring.entity.TutoringSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TutoringScheduleRepository extends JpaRepository<TutoringSchedule, Long> {
}
