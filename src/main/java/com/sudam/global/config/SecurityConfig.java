package com.sudam.global.config;

import com.sudam.global.security.exception.CustomAccessDeniedHandler;
import com.sudam.global.security.exception.CustomAuthenticationEntryPoint;
import com.sudam.global.security.filter.JwtAuthenticationFilter;
import com.sudam.global.security.oauth2.CustomOAuth2UserService;
import com.sudam.global.security.oauth2.handler.OAuth2LoginFailureHandler;
import com.sudam.global.security.oauth2.handler.OAuth2LoginSuccessHandler;
import com.sudam.global.security.token.TokenStore;
import com.sudam.global.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;

    private final String[] allowUris = {
        "/oauth2/**", // 소셜 로그인 페이지
        "/login/**", // 리다이렉트 uri
        "/api/v1/auth/reissue" // 토큰 재발급 uri
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)

                .httpBasic(AbstractHttpConfigurer::disable)

                .formLogin(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(allowUris).permitAll()
                        .anyRequest().authenticated())

                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler))

                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, tokenStore), UsernamePasswordAuthenticationFilter.class)

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler))

                .build();
    }
}
