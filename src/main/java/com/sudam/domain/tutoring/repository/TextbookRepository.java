package com.sudam.domain.tutoring.repository;

import com.sudam.domain.tutoring.entity.Textbook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TextbookRepository extends JpaRepository<Textbook, Long> {

    // 과외의 교재 전체를 ID 순으로. 기록 화면의 교재 목록 순서가 된다.
    List<Textbook> findAllByTutoringIdOrderById(Long tutoringId);
}
