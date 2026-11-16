package com.sudam.global.seed;

import com.sudam.domain.user.enums.Role;
import com.sudam.global.security.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoSeedRunnerTest {

    @Mock private DemoSeedService demoSeedService;
    @Mock private JwtUtil jwtUtil;

    @ParameterizedTest(name = "reset={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("실행기는 sudam.seed.reset 값을 그대로 시드 서비스에 넘긴다")
    void 리셋_플래그를_서비스에_전달(boolean reset) {
        // given
        DemoSeedRunner runner = new DemoSeedRunner(demoSeedService, jwtUtil, "", reset);
        given(demoSeedService.seed(DemoSeedRunner.DEFAULT_TEACHER_KAKAO_ID, reset))
                .willReturn(DemoSeedResult.skipped(1L, Role.TEACHER));

        // when
        runner.run(null);

        // then
        verify(demoSeedService).seed(DemoSeedRunner.DEFAULT_TEACHER_KAKAO_ID, reset);
    }
}
