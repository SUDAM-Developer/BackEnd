package com.sudam.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 현재 시각을 주입받기 위한 Clock 빈. DATETIME을 한국 시간으로 저장하므로(ERD 공통 규칙) Asia/Seoul 기준이다.
 * 서비스는 LocalDateTime.now(clock)으로 시각을 얻고, 테스트는 Clock.fixed를 넘긴다.
 */
@Configuration
public class ClockConfig {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SEOUL);
    }
}
