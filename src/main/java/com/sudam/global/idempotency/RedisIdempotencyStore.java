package com.sudam.global.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisIdempotencyStore implements IdempotencyStore {

    // 멱등 응답 키 접두사 (key=idem:{userId}:{Idempotency-Key} val={IdempotencyRecord JSON})
    private static final String KEY_PREFIX = "idem:";

    // 처리 중 표시는 요청 처리에 걸릴 수 있는 최대 시간보다 길어야 한다. 먼저 만료되면 재시도가 같은 입금을 한 번 더 실행한다.
    // 청구 행 잠금 대기(MySQL innodb_lock_wait_timeout 기본 50초)를 넉넉히 넘기도록 2분으로 둔다.
    // 대신 서버가 처리 도중 죽으면 그 키는 2분 동안 IDEMPOTENCY409_1이다.
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(2);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    // 값이 내 예약 그대로일 때만 바꾼다. GET과 SET/DEL 사이에 다른 요청이 끼지 않도록 스크립트 하나로 실행한다.
    private static final RedisScript<Long> COMPLETE_IF_OWNER = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
                return 1
            end
            return 0
            """, Long.class);

    private static final RedisScript<Long> DELETE_IF_OWNER = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public boolean tryReserve(Long userId, String key, IdempotencyRecord reservation) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(redisKey(userId, key), toJson(reservation), PROCESSING_TTL));
    }

    @Override
    public Optional<IdempotencyRecord> find(Long userId, String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(redisKey(userId, key)))
                .map(json -> jsonMapper.readValue(json, IdempotencyRecord.class));
    }

    @Override
    public void complete(Long userId, String key, IdempotencyRecord reservation, IdempotencyRecord response) {
        redisTemplate.execute(COMPLETE_IF_OWNER, List.of(redisKey(userId, key)),
                toJson(reservation), toJson(response), String.valueOf(COMPLETED_TTL.toSeconds()));
    }

    @Override
    public void release(Long userId, String key, IdempotencyRecord reservation) {
        redisTemplate.execute(DELETE_IF_OWNER, List.of(redisKey(userId, key)), toJson(reservation));
    }

    // 같은 레코드는 항상 같은 JSON이 되므로 저장된 문자열과 그대로 비교할 수 있다.
    private String toJson(IdempotencyRecord record) {
        return jsonMapper.writeValueAsString(record);
    }

    private String redisKey(Long userId, String key) {
        return KEY_PREFIX + userId + ":" + key;
    }
}
