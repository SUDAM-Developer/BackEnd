package com.sudam.global.idempotency;

import com.sudam.global.exception.ProjectException;
import com.sudam.global.exception.code.GeneralErrorCode;
import com.sudam.global.idempotency.exception.IdempotencyException;
import com.sudam.global.idempotency.exception.code.IdempotencyErrorCode;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * {@link Idempotent} 메서드의 멱등 처리.
 * 컨트롤러 직전에 키를 예약하고(처리 중 표시), 이미 끝난 키면 저장된 응답을 그대로 돌려준다.
 * 응답이 끝나면 2xx만 저장하고, 실패는 예약을 지워 같은 키로 다시 시도할 수 있게 한다.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$");

    // 이 요청이 키를 예약했음을 afterCompletion에 알리는 표시
    private static final String RESERVATION_ATTRIBUTE = IdempotencyInterceptor.class.getName() + ".reservation";

    private final IdempotencyStore idempotencyStore;

    /***
     * 함수 기능: 컨트롤러 실행 전에 멱등 키를 검사하고, 처음 온 키면 예약해 실행을 허락하고 이미 끝난 키면 저장된 응답을 재현한다.
     * 순서: 헤더 검사 → 지문 계산 → 예약 시도 → (실패 시) 저장된 레코드로 422 / 409 / 재현 중 하나를 고른다.
     * @param request  IdempotencyFilter가 감싼 요청 (본문을 다시 읽을 수 있어야 지문을 만든다)
     * @param response 재현할 때 저장된 상태 · Content-Type · 본문을 쓸 응답
     * @param handler  @Idempotent가 붙은 HandlerMethod일 때만 처리하고, 그 외는 그대로 통과시킨다
     * @return 컨트롤러를 실행해야 하면 true, 저장된 응답을 재현했으면 false
     * @throws IdempotencyException 헤더 없음 · UUID 아님(400), 같은 키 처리 중(409), 같은 키 · 다른 요청(422)
     * @throws ProjectException 필터가 감싸지 않은 요청 — 본문 64KB 초과 · chunked(413), JSON 아님(415)
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(Idempotent.class)) {
            return true;
        }

        String key = request.getHeader(HEADER);
        if (key == null || !UUID_PATTERN.matcher(key).matches()) {
            throw new IdempotencyException(IdempotencyErrorCode.KEY_INVALID);
        }

        Long userId = currentUserId();
        String fingerprint = fingerprint(request);
        // 토큰은 요청마다 달라서, 예약이 만료된 뒤 같은 키를 가져간 다른 요청의 예약과 구분된다.
        IdempotencyRecord reservation = IdempotencyRecord.processing(fingerprint, UUID.randomUUID().toString());

        if (reserve(request, userId, key, reservation)) {
            return true;
        }

        Optional<IdempotencyRecord> found = idempotencyStore.find(userId, key);

        // 확인하는 사이 예약이 만료되거나 해제됐으면 한 번만 다시 예약해 본다.
        if (found.isEmpty()) {
            if (reserve(request, userId, key, reservation)) {
                return true;
            }
            throw new IdempotencyException(IdempotencyErrorCode.REQUEST_IN_PROGRESS);
        }

        IdempotencyRecord record = found.get();
        if (!record.fingerprint().equals(fingerprint)) {
            throw new IdempotencyException(IdempotencyErrorCode.KEY_REUSED);
        }
        if (record.processing()) {
            throw new IdempotencyException(IdempotencyErrorCode.REQUEST_IN_PROGRESS);
        }

        replay(response, record);
        return false;
    }

    /***
     * 함수 기능: 이 요청이 예약한 키의 결과를 정리한다. 2xx면 응답을 24시간 저장하고, 그 외(4xx · 5xx · 처리되지 않은 예외)면
     * 예약을 지워 같은 키로 다시 시도할 수 있게 한다. 저장 · 삭제 모두 키에 아직 내 예약이 남아 있을 때만 반영된다.
     * 참고: 예외 처리기(@RestControllerAdvice)가 처리한 예외는 ex가 null로 오고 응답 상태가 4xx · 5xx로 이미 정해져 있다.
     * 여기서 던진 예외는 스프링이 로그만 남기고 삼키므로 클라이언트 응답에는 영향이 없다.
     * @param request  preHandle에서 남긴 예약 표시를 꺼낼 요청. 표시가 없으면(예약 안 함 · 재현함 · 거절함) 아무것도 하지 않는다
     * @param response IdempotencyFilter가 감싼 응답 (버퍼에 쌓인 본문 바이트를 저장한다)
     * @param ex       처리되지 않고 올라온 예외. 있으면 실패로 보고 예약을 지운다
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {

        if (!(request.getAttribute(RESERVATION_ATTRIBUTE) instanceof Reservation reservation)) {
            return;
        }

        ContentCachingResponseWrapper cachingResponse =
                WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);

        if (ex == null && cachingResponse != null && HttpStatus.valueOf(response.getStatus()).is2xxSuccessful()) {
            idempotencyStore.complete(reservation.userId(), reservation.key(), reservation.record(),
                    IdempotencyRecord.completed(reservation.record().fingerprint(), response.getStatus(),
                            response.getContentType(),
                            new String(cachingResponse.getContentAsByteArray(), StandardCharsets.UTF_8)));
            return;
        }

        idempotencyStore.release(reservation.userId(), reservation.key(), reservation.record());
    }

    /***
     * 함수 기능: 처리 중 예약을 시도하고, 성공하면 afterCompletion이 결과를 정리할 수 있도록 예약 정보를 요청에 남긴다.
     * @param reservation 지문과 이 요청만의 토큰을 담은 처리 중 레코드
     * @return 예약에 성공해 이 요청이 실행 권한을 얻었으면 true
     */
    private boolean reserve(HttpServletRequest request, Long userId, String key, IdempotencyRecord reservation) {
        if (!idempotencyStore.tryReserve(userId, key, reservation)) {
            return false;
        }
        request.setAttribute(RESERVATION_ATTRIBUTE, new Reservation(userId, key, reservation));
        return true;
    }

    /***
     * 함수 기능: 저장된 첫 응답(상태 코드 · Content-Type · 본문)을 그대로 쓰고, 재현된 응답임을 알리는 헤더를 붙인다.
     * 응답은 ContentCachingResponseWrapper 버퍼에 쌓였다가 IdempotencyFilter가 실제 응답으로 내보낸다.
     * @param record 완료 상태의 레코드
     */
    private void replay(HttpServletResponse response, IdempotencyRecord record) throws IOException {
        response.setStatus(record.status());
        if (record.contentType() != null) {
            response.setContentType(record.contentType());
        }
        response.setHeader(REPLAYED_HEADER, "true");
        response.getOutputStream().write(record.body().getBytes(StandardCharsets.UTF_8));
    }

    /***
     * 함수 기능: 메서드 · URI(쿼리 포함) · 본문으로 요청 지문을 만든다. 같은 키로 다른 요청이 왔는지 가려내는 데 쓴다.
     * @return SHA-256 64자 hex
     * @throws ProjectException 필터가 감싸지 않아 본문을 읽을 수 없는 요청 (413 / 415)
     */
    private String fingerprint(HttpServletRequest request) {
        CachedBodyRequestWrapper cached = WebUtils.getNativeRequest(request, CachedBodyRequestWrapper.class);
        // 필터가 감싸지 않은 요청은 본문이 너무 크거나(chunked 포함) JSON이 아니다. 본문을 읽지 않고 거절한다.
        if (cached == null) {
            throw new ProjectException(IdempotencyFilter.isTooLarge(request)
                    ? GeneralErrorCode.CONTENT_TOO_LARGE
                    : GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        return IdempotencyFingerprint.of(request.getMethod(), request.getRequestURI(), request.getQueryString(),
                cached.getBody());
    }

    /***
     * 함수 기능: 멱등 키를 회원별로 나누기 위해 SecurityContext에서 현재 회원 ID를 꺼낸다.
     * 인터셉터는 보안 필터 체인 안에서 돌기 때문에 JWT 인증 결과가 이미 채워져 있다.
     * @return 인증된 회원 ID
     * @throws IllegalStateException 인증 정보가 없을 때 (@Idempotent를 permitAll API에 붙인 설정 오류)
     */
    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // @Idempotent는 인증이 필요한 API에만 붙인다. 인증 없이 왔다면 보안 설정이 잘못된 것이다.
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomOAuth2User principal)) {
            throw new IllegalStateException("@Idempotent API는 인증된 회원만 호출할 수 있어야 합니다.");
        }
        return principal.getUserId();
    }

    // preHandle에서 건 예약을 afterCompletion까지 전달하는 값 (요청 속성에 담는다)
    private record Reservation(Long userId, String key, IdempotencyRecord record) {
    }
}
