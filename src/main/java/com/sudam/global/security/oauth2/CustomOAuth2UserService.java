package com.sudam.global.security.oauth2;

import com.sudam.domain.user.enums.Role;
import com.sudam.global.security.exception.AuthErrorCode;
import com.sudam.global.security.exception.AuthException;
import com.sudam.global.security.oauth2.response.KakaoResponse;
import com.sudam.global.security.oauth2.response.OAuth2Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final String KAKAO_REGISTRATION_ID = "kakao";
    private static final String KEY_ID = "id";
    private static final String KEY_KAKAO_ACCOUNT = "kakao_account";
    private static final String KEY_PROFILE = "profile";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_EMAIL = "email";

    /***
     * 함수 기능: 액세스 토큰으로 소셜 공급자의 사용자 정보를 조회하고, 인증 principal을 생성한다.
     * @param userRequest 공급자 등록 정보(registrationId, 엔드포인트)와 발급된 액세스 토큰을 담은 요청
     * @return 로그인한 회원의 userId와 권한을 담은 CustomOAuth2User
     * @throws OAuth2AuthenticationException 지원하지 않는 공급자이거나, 필수 동의 항목(이메일·닉네임)이
     *         누락된 경우, OAuth2LoginFailureHandler로 전달된다
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        // 액세스 토큰으로 사용자 정보와 공급자 조회
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // 얻은 사용자 정보를 OAuth2Response로 통일
        OAuth2Response oAuth2Response = switch (registrationId) {
            case KAKAO_REGISTRATION_ID -> toKakaoResponse(oAuth2User.getAttributes());
            default -> {
                log.warn("[소셜 로그인 실패] 지원하지 않는 소셜 로그인입니다. registrationId={}", registrationId);
                throw new AuthException(AuthErrorCode.UNSUPPORTED_PROVIDER);
            }
        };

        log.info("[소셜 회원 정보] provider={}, providerId={}, nickname={}, email={}",
                oAuth2Response.getProvider(), oAuth2Response.getProviderId(),
                oAuth2Response.getName(), oAuth2Response.getEmail());

        // TODO: provider + providerId로 회원을 조회하고 없으면 저장한 뒤, 실제 userId/role을 주입한다.
        return new CustomOAuth2User(1L, Role.USER);
    }

    /***
     * 함수 기능: 카카오로부터 받은 사용자 정보를 파싱하여 KakaoResponse 객체로 만든다.
     * @param attributes 카카오로부터 얻은 사용자 정보
     * @return OAuth2Response
     */
    private OAuth2Response toKakaoResponse(Map<String, Object> attributes) {

        // 동의하지 않은 항목은 키 자체가 응답에서 제외되므로 중첩 Map이 없을 수 있다.
        Object providerId = attributes.get(KEY_ID);
        Map<String, Object> kakaoAccount = asMap(attributes.get(KEY_KAKAO_ACCOUNT));
        Map<String, Object> profile = asMap(kakaoAccount.get(KEY_PROFILE));

        String email = (String) kakaoAccount.get(KEY_EMAIL);
        String name = (String) profile.get(KEY_NICKNAME);

        // 이메일·닉네임은 필수 동의 항목이다. 누락되면 인증을 중단한다.
        if (providerId == null || !StringUtils.hasText(email) || !StringUtils.hasText(name)) {
            log.warn("[소셜 로그인 실패] 필수 동의 항목이 누락되었습니다. providerId={}, email={}, name={}", providerId, email, name);
            throw new AuthException(AuthErrorCode.REQUIRED_CONSENT_MISSING);
        }

        return new KakaoResponse(String.valueOf(providerId), email, name);
    }

    /***
     * 함수 기능: 카카오로부터 받은 객체를 Map<String, Object> 형변환을 한다.
     *          SuppressWarnings: 확인되지 않은 형 변환에 대한 컴파일 경고를 억제한다.
     * @param value 객체
     * @return Map<String, Object>
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (value instanceof Map<?, ?> map) ? (Map<String, Object>) map : Map.of();
    }
}