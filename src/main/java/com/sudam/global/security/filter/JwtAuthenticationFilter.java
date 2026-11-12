package com.sudam.global.security.filter;

import com.sudam.domain.auth.exception.code.AuthErrorCode;
import com.sudam.domain.auth.exception.AuthException;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import com.sudam.global.security.token.TokenStore;
import com.sudam.global.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // 검증 실패 사유를 CustomAuthenticationEntryPoint에 넘기는 통로
    public static final String ERROR_CODE_ATTRIBUTE = "authErrorCode";

    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;

    /***
     * 함수 기능: Authorization 헤더의 액세스 토큰을 검증해 SecurityContext에 인증을 채운다.
     *          검증에 실패하면 응답을 쓰지 않고 사유만 request attribute에 담아 체인을 통과시킨다.
     *          인가 판단은 뒤쪽 AuthorizationFilter가 하고, 거부되면 ExceptionTranslationFilter가
     *          CustomAuthenticationEntryPoint를 호출한다.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        // 요청 헤더가 "Bearer "로 시작한다면 검증을 진행한다.
        if (header != null && header.startsWith(JwtUtil.BEARER_PREFIX)) {
            try {
                Claims claims = jwtUtil.parseAccessToken(header.substring(JwtUtil.BEARER_PREFIX.length()));

                // 로그아웃·탈퇴로 차단된 토큰은 서명이 멀쩡해도 거부한다.
                if (tokenStore.isBlacklisted(claims.getId())) {
                    throw new AuthException(AuthErrorCode.BLACKLISTED_TOKEN);
                }

                CustomOAuth2User principal = new CustomOAuth2User(
                        Long.parseLong(claims.getSubject()), jwtUtil.getRole(claims));

                // 인증에 성공했다면, SecurityContext에 인증 객체를 저장한다.
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

            } catch (AuthException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute(ERROR_CODE_ATTRIBUTE, e.getErrorCode());
                log.warn("[토큰 검증 실패] code={}, uri={}", e.getErrorCode().getCode(), request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }
}
