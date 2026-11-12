package com.sudam.domain.user.service;

import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /***
     * 함수 기능: 소셜 공급자 정보로 회원을 조회하고, 없으면 선생님으로 가입시킨다.
     * @param provider 소셜 공급자
     * @param providerId 공급자가 부여한 회원 식별자
     * @param email 공급자가 제공한 이메일
     * @return 조회되거나 새로 저장된 회원
     */
    @Transactional
    public User findOrCreate(Provider provider, String providerId, String email) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepository.save(User.createTeacher(provider, providerId, email)));
    }
}
