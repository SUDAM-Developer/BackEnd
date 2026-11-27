package com.sudam.global.idempotency;

import com.sudam.domain.user.enums.Role;
import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.GeneralErrorCode;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IdempotencyInterceptorTest.TestController.class)
@Import({IdempotencyInterceptorTest.TestController.class, IdempotencyInterceptorTest.TestConfig.class})
@DisplayName("IdempotencyInterceptor 필수 · 선택 모드와 예약 정리")
class IdempotencyInterceptorTest {

    private static final Long USER_ID = 1L;
    private static final String KEY = "3f1c2a9e-6b7d-4e21-9a0f-1c2d3e4f5a6b";
    private static final String BODY = "{\"amount\": 1}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryIdempotencyStore store;

    @Autowired
    private TestController controller;

    @BeforeEach
    void setUp() {
        store.clear();
        controller.calls.set(0);
    }

    @Test
    @DisplayName("같은 키 · 같은 요청을 두 번 보내면 한 번만 실행하고 두 번째는 첫 응답을 재현한다")
    void 같은_키_두_번은_재현() throws Exception {
        mockMvc.perform(request("/required", KEY, BODY))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyInterceptor.REPLAYED_HEADER));

        mockMvc.perform(request("/required", KEY, BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyInterceptor.REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.n").value(1));

        assertThat(controller.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키 · 다른 본문이면 422 · IDEMPOTENCY422_1")
    void 같은_키_다른_본문은_422() throws Exception {
        mockMvc.perform(request("/required", KEY, BODY)).andExpect(status().isCreated());

        mockMvc.perform(request("/required", KEY, "{\"amount\": 2}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY422_1"));

        assertThat(controller.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키 · 다른 경로면 422 · IDEMPOTENCY422_1")
    void 같은_키_다른_경로는_422() throws Exception {
        mockMvc.perform(request("/required", KEY, BODY)).andExpect(status().isCreated());

        mockMvc.perform(request("/optional", KEY, BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY422_1"));
    }

    @Test
    @DisplayName("필수 모드에서 헤더가 없으면 400 · IDEMPOTENCY400_1이고 실행하지 않는다")
    void 필수_헤더_없음은_400() throws Exception {
        mockMvc.perform(request("/required", null, BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY400_1"));

        assertThat(controller.calls.get()).isZero();
    }

    @Test
    @DisplayName("선택 모드에서 헤더가 없으면 멱등 처리 없이 매번 실행하고 저장소에 남기지 않는다")
    void 선택_헤더_없음_통과() throws Exception {
        mockMvc.perform(request("/optional", null, BODY)).andExpect(status().isCreated());
        mockMvc.perform(request("/optional", null, BODY))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyInterceptor.REPLAYED_HEADER));

        assertThat(controller.calls.get()).isEqualTo(2);
        assertThat(store.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("선택 모드여도 헤더가 UUID가 아니면 400 · IDEMPOTENCY400_1")
    void 선택_헤더_형식_오류는_400() throws Exception {
        mockMvc.perform(request("/optional", "abc", BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY400_1"));

        assertThat(controller.calls.get()).isZero();
    }

    @Test
    @DisplayName("선택 모드에서 헤더가 있으면 필수 모드와 똑같이 재현한다")
    void 선택_헤더_있으면_멱등() throws Exception {
        mockMvc.perform(request("/optional", KEY, BODY)).andExpect(status().isCreated());
        mockMvc.perform(request("/optional", KEY, BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyInterceptor.REPLAYED_HEADER, "true"));

        assertThat(controller.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("대소문자만 다른 UUID는 같은 키로 보고 재현한다")
    void 대소문자만_다른_키는_같은_키() throws Exception {
        mockMvc.perform(request("/required", KEY, BODY)).andExpect(status().isCreated());

        mockMvc.perform(request("/required", KEY.toUpperCase(), BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyInterceptor.REPLAYED_HEADER, "true"));

        assertThat(controller.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키의 첫 요청이 처리 중이면 409 · IDEMPOTENCY409_1")
    void 처리_중이면_409() throws Exception {
        String fingerprint = IdempotencyFingerprint.of("POST", "/test/idem/required", null,
                BODY.getBytes(StandardCharsets.UTF_8));
        store.put(USER_ID, KEY, IdempotencyRecord.processing(fingerprint, "first-token"));

        mockMvc.perform(request("/required", KEY, BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY409_1"));

        assertThat(controller.calls.get()).isZero();
    }

    @Test
    @DisplayName("4xx 응답이면 예약을 풀어 같은 키로 다시 실행할 수 있다")
    void 사백번대는_예약을_해제한다() throws Exception {
        mockMvc.perform(request("/bad", KEY, BODY)).andExpect(status().isBadRequest());
        mockMvc.perform(request("/bad", KEY, BODY)).andExpect(status().isBadRequest());

        assertThat(controller.calls.get()).isEqualTo(2);
        assertThat(store.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("5xx 응답이면 예약을 풀지 않아 같은 키의 즉시 재시도는 409로 막힌다 (커밋 후 실패 대비)")
    void 서버_오류는_예약을_유지한다() throws Exception {
        mockMvc.perform(request("/boom", KEY, BODY)).andExpect(status().isInternalServerError());

        mockMvc.perform(request("/boom", KEY, BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY409_1"));

        assertThat(controller.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("멱등 키가 붙은 본문이 64KB를 넘으면 413 · COMMON413")
    void 본문_64KB_초과는_413() throws Exception {
        String bigBody = "{\"pad\": \"" + "a".repeat(64 * 1024) + "\"}";

        mockMvc.perform(request("/required", KEY, bigBody))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("COMMON413"));

        assertThat(controller.calls.get()).isZero();
    }

    @Test
    @DisplayName("@Idempotent가 없는 핸들러는 헤더가 붙어도 그대로 처리한다")
    void 어노테이션_없는_핸들러는_통과() throws Exception {
        mockMvc.perform(get("/test/idem/plain").with(user()).header(IdempotencyInterceptor.HEADER, KEY))
                .andExpect(status().isOk());

        assertThat(store.isEmpty()).isTrue();
    }

    private static MockHttpServletRequestBuilder request(String path, String key, String body) {
        MockHttpServletRequestBuilder request = post("/test/idem" + path).with(user())
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return key == null ? request : request.header(IdempotencyInterceptor.HEADER, key);
    }

    private static RequestPostProcessor user() {
        CustomOAuth2User principal = new CustomOAuth2User(USER_ID, Role.TEACHER);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @RestController
    @RequestMapping("/test/idem")
    static class TestController {

        final AtomicInteger calls = new AtomicInteger();

        @Idempotent
        @PostMapping("/required")
        @ResponseStatus(HttpStatus.CREATED)
        Map<String, Integer> required(@RequestBody String body) {
            return Map.of("n", calls.incrementAndGet());
        }

        @Idempotent(required = false)
        @PostMapping("/optional")
        @ResponseStatus(HttpStatus.CREATED)
        Map<String, Integer> optional(@RequestBody String body) {
            return Map.of("n", calls.incrementAndGet());
        }

        @Idempotent
        @PostMapping("/bad")
        void bad(@RequestBody String body) {
            calls.incrementAndGet();
            throw new ProjectException(GeneralErrorCode.BAD_REQUEST);
        }

        @Idempotent
        @PostMapping("/boom")
        void boom(@RequestBody String body) {
            calls.incrementAndGet();
            throw new IllegalStateException("커밋 후 응답 직렬화 실패 흉내");
        }

        @GetMapping("/plain")
        Map<String, String> plain() {
            return Map.of("ok", "true");
        }
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }

        // 슬라이스에는 Redis 저장소 빈이 없으므로 같은 계약의 메모리 저장소를 쓴다.
        @Bean
        InMemoryIdempotencyStore idempotencyStore() {
            return new InMemoryIdempotencyStore();
        }
    }
}
