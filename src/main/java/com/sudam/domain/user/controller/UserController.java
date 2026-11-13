package com.sudam.domain.user.controller;

import com.sudam.domain.auth.service.AuthService;
import com.sudam.domain.user.dto.request.AgreeTermsRequest;
import com.sudam.domain.user.dto.request.SaveProfileRequest;
import com.sudam.domain.user.dto.response.MeResponse;
import com.sudam.domain.user.dto.response.ProfileCreateResponse;
import com.sudam.domain.user.dto.response.ProfileUpdateResponse;
import com.sudam.domain.user.dto.response.SettingsResponse;
import com.sudam.domain.user.dto.response.TermsAgreementResponse;
import com.sudam.domain.user.exception.code.UserSuccessCode;
import com.sudam.domain.user.service.UserService;
import com.sudam.global.ApiResponse;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import com.sudam.global.security.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원", description = "회원 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final CookieUtil cookieUtil;

    /***
     * 함수 기능: 본인 계정을 탈퇴 처리한다. 논리 삭제와 개인정보 파기는 DB에,
     *          리프레시 토큰 폐기와 액세스 토큰 차단은 로그아웃과 같은 처리를 재사용한다.
     * @param principal 인증된 회원
     * @param bearer Authorization 헤더. 인증이 필요한 경로라 값이 보장된다
     * @param response 리프레시 토큰 쿠키를 지울 응답
     */
    @Operation(summary = "회원 탈퇴",
            description = "본인 계정을 논리 삭제하고 개인정보를 파기한 뒤 로그아웃과 같은 토큰 정리를 수행한다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal CustomOAuth2User principal,
                                      @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
                                      HttpServletResponse response) {

        userService.withdraw(principal.getUserId());
        authService.logout(bearer);
        cookieUtil.deleteRefreshTokenCookie(response);

        return ApiResponse.onSuccess(UserSuccessCode.WITHDRAWN);
    }

    @Operation(summary = "내 정보 조회", description = "역할 · 이름 · 온보딩 상태를 돌려준다. 이름은 프로필이 없으면 null.")
    @GetMapping("/me")
    public ApiResponse<MeResponse> getMe(@AuthenticationPrincipal CustomOAuth2User principal) {
        return ApiResponse.onSuccess(UserSuccessCode.ME_FOUND, userService.getMe(principal.getUserId()));
    }

    @Operation(summary = "약관 동의",
            description = "코드별 현재 시행 중인 최신 버전에 동의한 것으로 기록한다. 필수 약관이 빠지면 USER400_1.")
    @PostMapping("/me/terms-agreements")
    public ResponseEntity<ApiResponse<TermsAgreementResponse>> agreeTerms(@AuthenticationPrincipal CustomOAuth2User principal,
                                                                         @Valid @RequestBody AgreeTermsRequest request) {
        TermsAgreementResponse response = userService.agreeTerms(principal.getUserId(), request);
        return ResponseEntity
                .status(UserSuccessCode.TERMS_AGREED.getStatus())
                .body(ApiResponse.onSuccess(UserSuccessCode.TERMS_AGREED, response));
    }

    @Operation(summary = "프로필 등록",
            description = "이름 · 과목을 등록한다. 이미 있으면 USER409_1, OTHER 선택 시 기타 과목명이 없으면 USER400_2.")
    @PostMapping("/me/profile")
    public ResponseEntity<ApiResponse<ProfileCreateResponse>> createProfile(@AuthenticationPrincipal CustomOAuth2User principal,
                                                                           @Valid @RequestBody SaveProfileRequest request) {
        ProfileCreateResponse response = userService.createProfile(principal.getUserId(), request);
        return ResponseEntity
                .status(UserSuccessCode.PROFILE_CREATED.getStatus())
                .body(ApiResponse.onSuccess(UserSuccessCode.PROFILE_CREATED, response));
    }

    @Operation(summary = "프로필 수정", description = "이름 · 과목을 전체 교체한다. 프로필이 없으면 USER404_2.")
    @PutMapping("/me/profile")
    public ApiResponse<ProfileUpdateResponse> updateProfile(@AuthenticationPrincipal CustomOAuth2User principal,
                                                            @Valid @RequestBody SaveProfileRequest request) {
        return ApiResponse.onSuccess(UserSuccessCode.PROFILE_UPDATED,
                userService.updateProfile(principal.getUserId(), request));
    }

    @Operation(summary = "설정 조회",
            description = "내 정보(이름 · 과목 · 로그인 수단)와 결제 · 알림 자리(고정 기본값)를 돌려준다. 프로필이 없으면 USER404_2.")
    @GetMapping("/me/settings")
    public ApiResponse<SettingsResponse> getSettings(@AuthenticationPrincipal CustomOAuth2User principal) {
        return ApiResponse.onSuccess(UserSuccessCode.SETTINGS_FOUND, userService.getSettings(principal.getUserId()));
    }
}
