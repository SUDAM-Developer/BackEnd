package com.sudam.domain.user.controller;

import com.sudam.domain.auth.service.AuthService;
import com.sudam.domain.user.exception.code.UserSuccessCode;
import com.sudam.domain.user.service.UserService;
import com.sudam.global.ApiResponse;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import com.sudam.global.security.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal CustomOAuth2User principal,
                                      @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
                                      HttpServletResponse response) {

        userService.withdraw(principal.getUserId());
        authService.logout(bearer);
        cookieUtil.deleteRefreshTokenCookie(response);

        return ApiResponse.onSuccess(UserSuccessCode.WITHDRAWN);
    }
}
