package com.sudam.global.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisIdempotencyStore 키 형식 · TTL · 직렬화 · 소유권 확인")
class RedisIdempotencyStoreTest {

    private static final IdempotencyRecord RESERVATION = IdempotencyRecord.processing("fp", "token-a");

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> ops;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private RedisIdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new RedisIdempotencyStore(redisTemplate, jsonMapper);
    }

    @Test
    @DisplayName("예약은 idem:{userId}:{key}에 NX · 2분 TTL로 저장하고 성공 여부를 돌려준다")
    void 예약은_NX와_2분_TTL로_저장한다() {
        // given
        given(redisTemplate.opsForValue()).willReturn(ops);
        given(ops.setIfAbsent(eq("idem:1:k"), anyString(), eq(Duration.ofMinutes(2)))).willReturn(true);

        // when & then
        assertThat(store.tryReserve(1L, "k", RESERVATION)).isTrue();
    }

    @Test
    @DisplayName("이미 키가 있으면 예약은 false다")
    void 이미_있으면_예약_실패() {
        // given
        given(redisTemplate.opsForValue()).willReturn(ops);
        given(ops.setIfAbsent(eq("idem:1:k"), anyString(), eq(Duration.ofMinutes(2)))).willReturn(false);

        // when & then
        assertThat(store.tryReserve(1L, "k", RESERVATION)).isFalse();
    }

    @Test
    @DisplayName("완료는 내 예약 값일 때만 24시간 TTL로 덮어쓰는 스크립트를 실행한다")
    void 완료는_내_예약일_때만_24시간으로_덮어쓴다() {
        // given
        IdempotencyRecord response = IdempotencyRecord.completed("fp", 201, "application/json", "{}");

        // when
        store.complete(1L, "k", RESERVATION, response);

        // then
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("idem:1:k")),
                eq(jsonMapper.writeValueAsString(RESERVATION)), eq(jsonMapper.writeValueAsString(response)),
                eq(String.valueOf(Duration.ofHours(24).toSeconds())));
    }

    @Test
    @DisplayName("해제는 내 예약 값일 때만 지우는 스크립트를 실행한다")
    void 해제는_내_예약일_때만_지운다() {
        // when
        store.release(1L, "k", RESERVATION);

        // then
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("idem:1:k")),
                eq(jsonMapper.writeValueAsString(RESERVATION)));
    }

    @Test
    @DisplayName("저장한 예약 JSON을 같은 레코드로 되읽는다")
    void 저장된_JSON을_레코드로_되읽는다() {
        // given
        given(redisTemplate.opsForValue()).willReturn(ops);
        store.tryReserve(1L, "k", RESERVATION);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(ops).setIfAbsent(eq("idem:1:k"), json.capture(), eq(Duration.ofMinutes(2)));
        given(ops.get("idem:1:k")).willReturn(json.getValue());

        // when & then
        assertThat(store.find(1L, "k")).contains(RESERVATION);
    }

    @Test
    @DisplayName("키가 없으면 빈 Optional이다")
    void 없으면_빈_Optional() {
        // given
        given(redisTemplate.opsForValue()).willReturn(ops);
        given(ops.get("idem:1:k")).willReturn(null);

        // when & then
        assertThat(store.find(1L, "k")).isEmpty();
    }

    @Test
    @DisplayName("같은 멱등 키라도 회원이 다르면 다른 Redis 키를 쓴다")
    void 회원별로_키가_분리된다() {
        // given
        given(redisTemplate.opsForValue()).willReturn(ops);

        // when
        store.tryReserve(1L, "k", RESERVATION);
        store.tryReserve(2L, "k", RESERVATION);

        // then
        verify(ops).setIfAbsent(eq("idem:1:k"), anyString(), eq(Duration.ofMinutes(2)));
        verify(ops).setIfAbsent(eq("idem:2:k"), anyString(), eq(Duration.ofMinutes(2)));
    }
}
