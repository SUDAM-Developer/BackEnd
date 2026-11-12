package com.sudam.domain.auth.controller;

import com.sudam.domain.auth.exception.code.AuthSuccessCode;
import com.sudam.domain.auth.dto.TokenPair;
import com.sudam.domain.auth.dto.response.TokenReissueResponse;
import com.sudam.domain.auth.service.AuthService;
import com.sudam.global.ApiResponse;
import com.sudam.global.security.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "토큰 재발급·로그아웃 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    /***
     * 함수 기능: 리프레시 토큰 쿠키로 액세스·리프레시 토큰을 재발급한다.
     *          액세스 토큰은 응답 본문, 새 리프레시 토큰은 HttpOnly 쿠키로 내려준다.
     * @param refreshToken 리프레시 토큰 쿠키. 없으면 null이 들어오고 서비스가 AUTH401_3으로 막는다
     * @param response 새 쿠키를 실을 응답
     * @return 새 액세스 토큰
     */
    @Operation(summary = "토큰 재발급",
            description = "리프레시 토큰 쿠키로 액세스·리프레시 토큰을 재발급한다. 새 리프레시 토큰은 HttpOnly 쿠키로 내려간다.")
    @SecurityRequirements // 액세스 토큰이 만료된 상태로 호출하는 경로라 인증을 요구하지 않는다
    @PostMapping("/reissue")
    public ApiResponse<TokenReissueResponse> reissue(
            @CookieValue(name = CookieUtil.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {

        TokenPair tokens = authService.reissue(refreshToken);
        cookieUtil.addRefreshTokenCookie(response, tokens.refreshToken());

        return ApiResponse.onSuccess(AuthSuccessCode.TOKEN_REISSUED,
                new TokenReissueResponse(tokens.accessToken()));
    }

    /***
     * 함수 기능: 리프레시 토큰을 폐기하고 지금 쓰인 액세스 토큰을 차단해 로그아웃한다.
     * @param bearer Authorization 헤더. 인증이 필요한 경로라 값이 보장된다
     * @param response 리프레시 토큰 쿠키를 지울 응답
     */
    @Operation(summary = "로그아웃",
            description = "리프레시 토큰을 폐기하고 요청에 쓰인 액세스 토큰을 차단 목록에 올린다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
                                    HttpServletResponse response) {

        authService.logout(bearer);
        cookieUtil.deleteRefreshTokenCookie(response);

        return ApiResponse.onSuccess(AuthSuccessCode.LOGGED_OUT);
    }
}
