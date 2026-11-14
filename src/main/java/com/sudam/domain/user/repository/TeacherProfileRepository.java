package com.sudam.domain.user.repository;

import com.sudam.domain.user.entity.TeacherProfile;
import org.springframework.data.jpa.repository.JpaRepository;

// PK가 users.id라서 findById / existsById가 곧 회원 기준 조회다.
public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, Long> {
}
