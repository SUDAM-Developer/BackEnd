package com.sudam.global.config;

import com.sudam.global.idempotency.IdempotencyInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final IdempotencyInterceptor idempotencyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // @Idempotent가 붙은 핸들러에서만 동작하므로 경로 제한 없이 등록한다.
        registry.addInterceptor(idempotencyInterceptor);
    }
}
