package com.sudam.domain.tutoring.repository;

import com.sudam.domain.tutoring.entity.Textbook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TextbookRepository extends JpaRepository<Textbook, Long> {
}
