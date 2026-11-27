package com.sudam.global.idempotency;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 슬라이스 테스트용 멱등 저장소. Redis 없이 같은 계약(키 없을 때만 예약, 내 예약일 때만 완료 · 해제)을 지킨다.
 * TTL은 다루지 않으므로 만료는 put으로 다른 예약을 깔아 흉내 낸다.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

    @Override
    public boolean tryReserve(Long userId, String key, IdempotencyRecord reservation) {
        return records.putIfAbsent(id(userId, key), reservation) == null;
    }

    @Override
    public Optional<IdempotencyRecord> find(Long userId, String key) {
        return Optional.ofNullable(records.get(id(userId, key)));
    }

    @Override
    public void complete(Long userId, String key, IdempotencyRecord reservation, IdempotencyRecord response) {
        records.replace(id(userId, key), reservation, response);
    }

    @Override
    public void release(Long userId, String key, IdempotencyRecord reservation) {
        records.remove(id(userId, key), reservation);
    }

    // 처리 중 상태나 만료 후 다른 요청의 예약을 테스트에서 미리 깔아 둘 때 쓴다.
    public void put(Long userId, String key, IdempotencyRecord record) {
        records.put(id(userId, key), record);
    }

    public void clear() {
        records.clear();
    }

    private String id(Long userId, String key) {
        return userId + ":" + key;
    }
}
