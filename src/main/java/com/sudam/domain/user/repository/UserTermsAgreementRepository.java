package com.sudam.domain.user.repository;

import com.sudam.domain.user.entity.Terms;
import com.sudam.domain.user.entity.UserTermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {

    List<UserTermsAgreement> findByUserIdAndTermsIn(Long userId, Collection<Terms> terms);
}
