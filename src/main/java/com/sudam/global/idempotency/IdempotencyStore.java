package com.sudam.global.idempotency;

import java.util.Optional;

/**
 * 멱등 키별 처리 상태와 첫 응답을 보관하는 저장소. 키는 회원 단위로 분리된다.
 * 완료 · 해제는 키에 아직 "내 예약"이 남아 있을 때만 반영한다. 예약이 만료돼 다른 요청이 같은 키를 가져갔다면
 * 그 요청의 예약이나 완료 응답을 덮어쓰거나 지우면 안 되기 때문이다.
 */
public interface IdempotencyStore {

    /***
     * 함수 기능: 키가 비어 있을 때만 처리 중 예약을 건다. 동시에 온 같은 키 중 하나만 성공한다.
     * @param reservation IdempotencyRecord.processing(지문, 요청마다 다른 토큰)
     * @return 예약에 성공하면 true
     */
    boolean tryReserve(Long userId, String key, IdempotencyRecord reservation);

    /***
     * 함수 기능: 키에 저장된 레코드(처리 중 또는 완료)를 조회한다.
     * @return 없거나 만료됐으면 빈 Optional
     */
    Optional<IdempotencyRecord> find(Long userId, String key);

    /***
     * 함수 기능: 키에 내 예약이 그대로 있을 때만 완료 응답으로 덮어쓴다.
     */
    void complete(Long userId, String key, IdempotencyRecord reservation, IdempotencyRecord response);

    /***
     * 함수 기능: 키에 내 예약이 그대로 있을 때만 지워 같은 키로 다시 시도할 수 있게 한다. 실패 응답 때 호출한다.
     */
    void release(Long userId, String key, IdempotencyRecord reservation);
}
