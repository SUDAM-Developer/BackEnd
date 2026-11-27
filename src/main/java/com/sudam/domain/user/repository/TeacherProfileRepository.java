package com.sudam.domain.user.repository;

import com.sudam.domain.user.entity.TeacherProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// PK가 users.id라서 findById / existsById가 곧 회원 기준 조회다.
public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, Long> {

    // 첫 과외 생성처럼 프로필 값을 읽고 바꾸는 흐름을 한 번에 하나만 통과시킨다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tp from TeacherProfile tp where tp.id = :id")
    Optional<TeacherProfile> findByIdForUpdate(@Param("id") Long id);
}
