package com.sudam.domain.user.service;

import com.sudam.domain.user.dto.request.AgreeTermsRequest;
import com.sudam.domain.user.dto.request.SaveProfileRequest;
import com.sudam.domain.user.dto.response.MeResponse;
import com.sudam.domain.user.dto.response.ProfileCreateResponse;
import com.sudam.domain.user.dto.response.ProfileUpdateResponse;
import com.sudam.domain.user.dto.response.SettingsResponse;
import com.sudam.domain.user.dto.response.TermsAgreementResponse;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.Terms;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.entity.UserTermsAgreement;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import com.sudam.domain.user.enums.TermsCode;
import com.sudam.domain.user.enums.UserStatus;
import com.sudam.domain.user.exception.UserException;
import com.sudam.domain.user.exception.code.UserErrorCode;
import com.sudam.domain.user.repository.TeacherProfileRepository;
import com.sudam.domain.user.repository.TermsRepository;
import com.sudam.domain.user.repository.UserRepository;
import com.sudam.domain.user.repository.UserTermsAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final TermsRepository termsRepository;
    private final UserTermsAgreementRepository userTermsAgreementRepository;
    private final TeacherProfileRepository teacherProfileRepository;

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
        findUser(userId).withdraw();
    }

    /***
     * 함수 기능: 로그인한 회원의 역할·이름·온보딩 상태를 조회한다. 상태는 저장하지 않고 계산한다.
     * @param userId 회원 식별자
     * @return 역할, 프로필 이름(없으면 null), 온보딩 상태
     */
    public MeResponse getMe(Long userId) {
        User user = findUser(userId);
        Optional<TeacherProfile> profile = teacherProfileRepository.findById(userId);
        UserStatus status = resolveStatus(userId, profile.isPresent());

        return new MeResponse(user.getRole(), profile.map(TeacherProfile::getName).orElse(null), status);
    }

    /***
     * 함수 기능: 요청한 약관 코드마다 현재 시행 중인 최신 버전에 동의한 것으로 기록한다.
     *          이미 같은 버전에 동의했으면 행을 새로 만들지 않고 기존 동의 시각을 그대로 돌려준다.
     * @param userId 회원 식별자
     * @param request 동의한 약관 코드 목록
     * @return 코드별 동의 시각
     * @throws UserException 현재 시행 중인 필수 약관 코드가 하나라도 빠진 경우 (저장 전에 검사하므로 부분 저장 없음)
     */
    @Transactional
    public TermsAgreementResponse agreeTerms(Long userId, AgreeTermsRequest request) {

        // 요청 코드(중복 제거)와 현재 시행 중인 약관(코드별 최신 버전)
        Set<TermsCode> requested = EnumSet.copyOf(request.termsCodes());
        Map<TermsCode, Terms> current = currentTerms();

        // 필수 약관이 하나라도 빠지면 저장 전에 거부한다
        boolean requiredMissing = current.values().stream()
                .anyMatch(terms -> terms.isRequired() && !requested.contains(terms.getCode()));
        if (requiredMissing) {
            throw new UserException(UserErrorCode.REQUIRED_TERMS_MISSING);
        }

        // 요청 코드에 해당하는 약관 행과, 그중 이미 동의한 행(terms_id 기준)
        List<Terms> targets = requested.stream().map(current::get).filter(Objects::nonNull).toList();
        Map<Long, UserTermsAgreement> existing = userTermsAgreementRepository.findByUserIdAndTermsIn(userId, targets)
                .stream()
                .collect(Collectors.toMap(agreement -> agreement.getTerms().getId(), Function.identity()));

        // 동의 행이 없는 약관만 새로 저장하고, 있는 것은 그대로 쓴다 (멱등)
        User user = findUser(userId);
        List<UserTermsAgreement> agreements = new ArrayList<>();
        for (Terms terms : targets) {
            UserTermsAgreement agreement = existing.get(terms.getId());
            if (agreement == null) {
                agreement = userTermsAgreementRepository.save(UserTermsAgreement.agree(user, terms));
            }
            agreements.add(agreement);
        }

        return TermsAgreementResponse.from(agreements);
    }

    /***
     * 함수 기능: 프로필(이름 · 과목)을 등록한다.
     * @throws UserException 이미 프로필이 있거나, OTHER를 골랐는데 기타 과목명이 없는 경우
     */
    @Transactional
    public ProfileCreateResponse createProfile(Long userId, SaveProfileRequest request) {
        if (teacherProfileRepository.existsById(userId)) {
            throw new UserException(UserErrorCode.PROFILE_ALREADY_EXISTS);
        }

        User user = findUser(userId);
        TeacherProfile profile = teacherProfileRepository.save(
                TeacherProfile.create(user, request.name(), request.subjects(), subjectOtherOf(request)));

        return new ProfileCreateResponse(profile.getCreatedAt());
    }

    /***
     * 함수 기능: 프로필(이름 · 과목)을 전체 교체한다.
     * @throws UserException 프로필이 없거나, OTHER를 골랐는데 기타 과목명이 없는 경우
     */
    @Transactional
    public ProfileUpdateResponse updateProfile(Long userId, SaveProfileRequest request) {
        TeacherProfile profile = findProfile(userId);
        profile.update(request.name(), request.subjects(), subjectOtherOf(request));

        teacherProfileRepository.flush();

        return new ProfileUpdateResponse(profile.getUpdatedAt());
    }

    /***
     * 함수 기능: 설정 화면 정보를 조회한다. 프로필 · 로그인 수단만 DB 값이고 나머지는 고정 기본값이다.
     * @throws UserException 프로필이 없는 경우
     */
    public SettingsResponse getSettings(Long userId) {
        TeacherProfile profile = findProfile(userId);
        return SettingsResponse.from(profile, profile.getUser().getProvider());
    }

    // 온보딩 상태 = 필수 약관 전부 동의 여부 → 프로필 존재 여부 순으로 판단한다.
    private UserStatus resolveStatus(Long userId, boolean hasProfile) {

        // 동의가 필수인 약관 목록을 가져온다.
        List<Terms> required = currentTerms().values().stream().filter(Terms::isRequired).toList();
        // 위 약관 목록 중 회원이 동의한 갯수를 센다.
        int agreedCount = userTermsAgreementRepository.findByUserIdAndTermsIn(userId, required).size();

        // 요구하는 동의 약관보다 회원의 동의 약관이 적다면 -> 필수인 약관을 동의하지 않은 것으로 판단.
        if (agreedCount < required.size()) {
            return UserStatus.TERMS_REQUIRED;
        }
        if (!hasProfile) {
            return UserStatus.PROFILE_REQUIRED;
        }
        return UserStatus.ACTIVE;
    }

    // 시행 중인 약관을 code별 최신 version 하나로 줄인다.
    private Map<TermsCode, Terms> currentTerms() {
        return termsRepository.findByEffectiveAtLessThanEqual(LocalDateTime.now()).stream()
                .collect(Collectors.toMap(Terms::getCode, Function.identity(),
                        BinaryOperator.maxBy(Comparator.comparingInt(Terms::getVersion))));
    }

    // OTHER가 없으면 기타 과목명은 무시하고 null, 있으면 필수다.
    private String subjectOtherOf(SaveProfileRequest request) {
        if (!request.subjects().contains(Subject.OTHER)) {
            return null;
        }
        if (!StringUtils.hasText(request.subjectOther())) {
            throw new UserException(UserErrorCode.SUBJECT_OTHER_REQUIRED);
        }
        return request.subjectOther().trim();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    private TeacherProfile findProfile(Long userId) {
        return teacherProfileRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.PROFILE_NOT_FOUND));
    }
}
