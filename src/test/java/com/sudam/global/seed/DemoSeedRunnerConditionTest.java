package com.sudam.global.seed;

import com.sudam.global.security.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DemoSeedRunnerConditionTest {

    // 조건만 확인한다. 빈이 생기지 않으면 의존성(레포지토리 등)도 필요 없다.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DemoSeedRunner.class, DemoSeedService.class);

    @Test
    @DisplayName("sudam.seed.enabled가 없으면 실행기와 서비스 빈이 생기지 않는다")
    void 설정값이_없으면_빈_없음() {
        // when & then
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(DemoSeedRunner.class);
            assertThat(context).doesNotHaveBean(DemoSeedService.class);
        });
    }

    @Test
    @DisplayName("sudam.seed.enabled=false면 실행기와 서비스 빈이 생기지 않는다")
    void 설정값이_false면_빈_없음() {
        // when & then
        contextRunner.withPropertyValues("sudam.seed.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(DemoSeedRunner.class);
            assertThat(context).doesNotHaveBean(DemoSeedService.class);
        });
    }

    @Test
    @DisplayName("sudam.seed.enabled=true면 실행기 빈이 생긴다")
    void 설정값이_true면_빈_생성() {
        // given: 서비스의 레포지토리 의존성 대신 서비스 자체를 가짜로 둔다
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(DemoSeedRunner.class)
                .withBean(DemoSeedService.class, () -> mock(DemoSeedService.class))
                .withBean(JwtUtil.class, () -> mock(JwtUtil.class));

        // when & then
        runner.withPropertyValues("sudam.seed.enabled=true").run(context ->
                assertThat(context).hasSingleBean(DemoSeedRunner.class));
    }
}
