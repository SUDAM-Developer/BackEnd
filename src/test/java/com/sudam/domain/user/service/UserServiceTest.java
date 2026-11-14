package com.sudam.domain.user.service;

import com.sudam.domain.user.dto.request.AgreeTermsRequest;
import com.sudam.domain.user.dto.request.SaveProfileRequest;
import com.sudam.domain.user.dto.response.MeResponse;
import com.sudam.domain.user.dto.response.TermsAgreementResponse;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.Terms;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.entity.UserTermsAgreement;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Role;
import com.sudam.domain.user.enums.Subject;
import com.sudam.domain.user.enums.TermsCode;
import com.sudam.domain.user.enums.UserStatus;
import com.sudam.domain.user.exception.UserException;
import com.sudam.domain.user.repository.TeacherProfileRepository;
import com.sudam.domain.user.repository.TermsRepository;
import com.sudam.domain.user.repository.UserRepository;
import com.sudam.domain.user.repository.UserTermsAgreementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 온보딩(내 정보 · 약관 동의 · 프로필)")
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final List<TermsCode> ALL_CODES = List.of(TermsCode.SERVICE, TermsCode.PRIVACY, TermsCode.CONTACT);

    @Mock
    private UserRepository userRepository;
    @Mock
    private TermsRepository termsRepository;
    @Mock
    private UserTermsAgreementRepository userTermsAgreementRepository;
    @Mock
    private TeacherProfileRepository teacherProfileRepository;

    @InjectMocks
    private UserService userService;

    private User user;
    private Terms service;
    private Terms privacy;
    private Terms contact;

    @BeforeEach
    void setUp() {
        user = User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app");
        service = terms(1L, TermsCode.SERVICE, 1);
        privacy = terms(2L, TermsCode.PRIVACY, 1);
        contact = terms(3L, TermsCode.CONTACT, 1);
    }

    // Terms는 시드 전용이라 생성자가 없다. 테스트마다 쓰는 getter가 달라 이 목만 LENIENT로 둔다.
    private Terms terms(long id, TermsCode code, int version) {
        Terms terms = mock(Terms.class, withSettings().strictness(Strictness.LENIENT));
        given(terms.getId()).willReturn(id);
        given(terms.getCode()).willReturn(code);
        given(terms.getVersion()).willReturn(version);
        given(terms.isRequired()).willReturn(true);
        return terms;
    }

    // 현재 시행 중인 약관은 3개, 그중 회원이 동의한 약관 행만 돌려주도록 깔아 둔다.
    private void givenAgreed(Terms... agreed) {
        given(termsRepository.findByEffectiveAtLessThanEqual(any())).willReturn(List.of(service, privacy, contact));
        given(userTermsAgreementRepository.findByUserIdAndTermsIn(eq(USER_ID), anyCollection()))
                .willReturn(List.of(agreed).stream().map(t -> UserTermsAgreement.agree(user, t)).toList());
    }

    @Test
    @DisplayName("필수 약관에 하나라도 동의하지 않았으면 상태는 TERMS_REQUIRED이고 이름은 null이다")
    void 약관_미동의면_TERMS_REQUIRED() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(teacherProfileRepository.findById(USER_ID)).willReturn(Optional.empty());
        givenAgreed(service, privacy);

        // when
        MeResponse me = userService.getMe(USER_ID);

        // then
        assertThat(me.role()).isEqualTo(Role.TEACHER);
        assertThat(me.name()).isNull();
        assertThat(me.status()).isEqualTo(UserStatus.TERMS_REQUIRED);
    }

    @Test
    @DisplayName("필수 약관에 모두 동의했지만 프로필이 없으면 상태는 PROFILE_REQUIRED다")
    void 프로필_없으면_PROFILE_REQUIRED() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(teacherProfileRepository.findById(USER_ID)).willReturn(Optional.empty());
        givenAgreed(service, privacy, contact);

        // when
        MeResponse me = userService.getMe(USER_ID);

        // then
        assertThat(me.status()).isEqualTo(UserStatus.PROFILE_REQUIRED);
    }

    @Test
    @DisplayName("약관 동의와 프로필이 모두 있으면 상태는 ACTIVE이고 프로필 이름을 돌려준다")
    void 프로필_있으면_ACTIVE() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(teacherProfileRepository.findById(USER_ID))
                .willReturn(Optional.of(TeacherProfile.create(user, "한서윤", List.of(Subject.MATH), null)));
        givenAgreed(service, privacy, contact);

        // when
        MeResponse me = userService.getMe(USER_ID);

        // then
        assertThat(me.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(me.name()).isEqualTo("한서윤");
    }

    @Test
    @DisplayName("필수 약관이 빠진 동의 요청은 USER400_1이고 아무것도 저장하지 않는다")
    void 필수_약관_누락이면_저장_없이_예외() {
        // given
        given(termsRepository.findByEffectiveAtLessThanEqual(any())).willReturn(List.of(service, privacy, contact));
        AgreeTermsRequest request = new AgreeTermsRequest(List.of(TermsCode.SERVICE, TermsCode.PRIVACY));

        // when & then
        assertThatThrownBy(() -> userService.agreeTerms(USER_ID, request))
                .isInstanceOf(UserException.class)
                .extracting(e -> ((UserException) e).getErrorCode().getCode())
                .isEqualTo("USER400_1");

        verify(userTermsAgreementRepository, never()).save(any());
    }

    @Test
    @DisplayName("처음 동의하면 요청한 약관마다 동의 행을 저장하고 코드별 동의 시각을 돌려준다")
    void 처음_동의하면_모두_저장() {
        // given
        givenAgreed();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userTermsAgreementRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // when
        TermsAgreementResponse response = userService.agreeTerms(USER_ID, new AgreeTermsRequest(ALL_CODES));

        // then
        assertThat(response.agreements())
                .extracting(TermsAgreementResponse.Item::code)
                .containsExactlyInAnyOrderElementsOf(ALL_CODES);
        verify(userTermsAgreementRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("이미 동의한 약관은 다시 저장하지 않고 기존 동의 시각을 그대로 돌려준다")
    void 이미_동의한_약관은_재사용() {
        // given — SERVICE · PRIVACY는 동의되어 있고 CONTACT만 새로 동의하는 상황
        givenAgreed(service, privacy);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userTermsAgreementRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // when
        TermsAgreementResponse response = userService.agreeTerms(USER_ID, new AgreeTermsRequest(ALL_CODES));

        // then
        assertThat(response.agreements()).hasSize(3);

        ArgumentCaptor<UserTermsAgreement> saved = ArgumentCaptor.forClass(UserTermsAgreement.class);
        verify(userTermsAgreementRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getTerms()).isSameAs(contact);
    }

    @Test
    @DisplayName("같은 코드의 약관이 여러 버전 시행 중이면 최신 버전에 동의한 것으로 기록한다")
    void 최신_버전에_동의() {
        // given — SERVICE v1 · v2가 모두 시행 중이고 PRIVACY · CONTACT는 선택 약관인 상황
        Terms serviceV2 = terms(4L, TermsCode.SERVICE, 2);
        given(privacy.isRequired()).willReturn(false);
        given(contact.isRequired()).willReturn(false);
        given(termsRepository.findByEffectiveAtLessThanEqual(any()))
                .willReturn(List.of(service, serviceV2, privacy, contact));
        given(userTermsAgreementRepository.findByUserIdAndTermsIn(eq(USER_ID), anyCollection())).willReturn(List.of());
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userTermsAgreementRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // when
        userService.agreeTerms(USER_ID, new AgreeTermsRequest(List.of(TermsCode.SERVICE)));

        // then
        ArgumentCaptor<UserTermsAgreement> saved = ArgumentCaptor.forClass(UserTermsAgreement.class);
        verify(userTermsAgreementRepository).save(saved.capture());
        assertThat(saved.getValue().getTerms()).isSameAs(serviceV2);
    }

    @Test
    @DisplayName("프로필이 이미 있으면 USER409_1 예외가 발생한다")
    void 프로필_중복이면_예외() {
        // given
        given(teacherProfileRepository.existsById(USER_ID)).willReturn(true);
        SaveProfileRequest request = new SaveProfileRequest("한서윤", List.of(Subject.MATH), null);

        // when & then
        assertThatThrownBy(() -> userService.createProfile(USER_ID, request))
                .isInstanceOf(UserException.class)
                .extracting(e -> ((UserException) e).getErrorCode().getCode())
                .isEqualTo("USER409_1");

        verify(teacherProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("기타 과목을 골랐는데 기타 과목명이 공백이면 USER400_2 예외가 발생한다")
    void 기타_과목명_없으면_예외() {
        // given
        given(teacherProfileRepository.existsById(USER_ID)).willReturn(false);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        SaveProfileRequest request = new SaveProfileRequest("한서윤", List.of(Subject.OTHER), "  ");

        // when & then
        assertThatThrownBy(() -> userService.createProfile(USER_ID, request))
                .isInstanceOf(UserException.class)
                .extracting(e -> ((UserException) e).getErrorCode().getCode())
                .isEqualTo("USER400_2");

        verify(teacherProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("기타 과목을 고르지 않았으면 함께 보낸 기타 과목명은 버리고 null로 저장한다")
    void 기타_과목_없으면_과목명은_null() {
        // given
        given(teacherProfileRepository.existsById(USER_ID)).willReturn(false);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(teacherProfileRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        SaveProfileRequest request = new SaveProfileRequest("한서윤", List.of(Subject.ENGLISH), "논술");

        // when
        userService.createProfile(USER_ID, request);

        // then
        ArgumentCaptor<TeacherProfile> saved = ArgumentCaptor.forClass(TeacherProfile.class);
        verify(teacherProfileRepository).save(saved.capture());
        assertThat(saved.getValue().getSubjectOther()).isNull();
        assertThat(saved.getValue().getSubjects()).containsExactly(Subject.ENGLISH);
    }

    @Test
    @DisplayName("프로필이 없는 회원이 수정을 요청하면 USER404_2 예외가 발생한다")
    void 프로필_없으면_수정_불가() {
        // given
        given(teacherProfileRepository.findById(USER_ID)).willReturn(Optional.empty());
        SaveProfileRequest request = new SaveProfileRequest("한서윤", List.of(Subject.MATH), null);

        // when & then
        assertThatThrownBy(() -> userService.updateProfile(USER_ID, request))
                .isInstanceOf(UserException.class)
                .extracting(e -> ((UserException) e).getErrorCode().getCode())
                .isEqualTo("USER404_2");
    }
}
