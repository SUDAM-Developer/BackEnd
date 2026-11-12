package com.sudam.domain.user.service;

import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.exception.UserException;
import com.sudam.domain.user.exception.code.UserErrorCode;
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

    /***
     * 함수 기능: 회원을 탈퇴 처리한다. 토큰 폐기는 호출하는 쪽에서 따로 맡는다.
     * @param userId 탈퇴할 회원 식별자
     * @throws UserException 해당 회원이 없는 경우
     */
    @Transactional
    public void withdraw(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND))
                .withdraw();
    }
}
