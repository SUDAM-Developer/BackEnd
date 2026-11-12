package com.sudam.domain.user.repository;

import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);
}
