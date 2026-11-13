package com.sudam.domain.tutoring.repository;

import com.sudam.domain.tutoring.entity.Tutoring;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TutoringRepository extends JpaRepository<Tutoring, Long> {
}
