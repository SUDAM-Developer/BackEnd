package com.sudam.domain.tutoring.repository;

import com.sudam.domain.tutoring.entity.TutoringSetting;
import org.springframework.data.jpa.repository.JpaRepository;

// PK가 tutoring_id라 findById(tutoringId)로 과외의 공유 설정을 바로 찾는다.
public interface TutoringSettingRepository extends JpaRepository<TutoringSetting, Long> {
}
