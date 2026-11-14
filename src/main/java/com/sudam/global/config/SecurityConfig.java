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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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
        "/api/v1/auth/reissue", // 토큰 재발급 uri
        "/swagger-ui/**", // Swagger UI 정적 리소스
        "/swagger-ui.html", // Swagger UI 진입 경로
        "/v3/api-docs/**" // OpenAPI 문서(JSON)
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // 아래 corsConfigurationSource 빈을 쓴다. 프리플라이트(OPTIONS)는 인증 검사 전에 여기서 응답한다.
                .cors(Customizer.withDefaults())

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

    /***
     * 함수 기능: 프론트 출처에서 오는 요청을 허용하는 CORS 설정을 만든다.
     *          RT 쿠키(재발급 · 로그아웃)를 주고받아야 하므로 자격 증명을 허용하고, 그래서 출처는 "*"가 아니라 목록으로 지정한다.
     * @param allowedOrigins 허용할 프론트 출처 (프로파일별 app.cors.allowed-origins)
     * @return 모든 경로에 적용되는 CORS 설정
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
