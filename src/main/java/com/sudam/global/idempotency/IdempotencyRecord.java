package com.sudam.global.idempotency;

/**
 * 멱등 키 하나에 저장되는 값. 처리 중이면 예약 토큰과 지문만, 완료면 첫 응답(상태 · Content-Type · 본문)까지 담는다.
 *
 * @param processing  첫 요청이 아직 처리 중이면 true
 * @param token       처리 중 예약을 건 요청의 고유 값. 완료 · 해제할 때 내 예약인지 가려낸다 (완료면 null)
 * @param fingerprint 첫 요청의 지문 (같은 키 · 다른 요청 판별용)
 * @param status      첫 응답 상태 코드 (처리 중이면 0)
 * @param contentType 첫 응답 Content-Type (처리 중이면 null)
 * @param body        첫 응답 본문 UTF-8 문자열 (처리 중이면 null)
 */
public record IdempotencyRecord(boolean processing, String token, String fingerprint, int status,
                                String contentType, String body) {

    public static IdempotencyRecord processing(String fingerprint, String token) {
        return new IdempotencyRecord(true, token, fingerprint, 0, null, null);
    }

    public static IdempotencyRecord completed(String fingerprint, int status, String contentType, String body) {
        return new IdempotencyRecord(false, null, fingerprint, status, contentType, body);
    }
}
