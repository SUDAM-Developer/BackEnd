package com.sudam.global.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Set;

/**
 * Idempotency-Key 헤더가 붙은 작은 JSON 쓰기 요청만 요청 · 응답을 감싼다.
 * 인터셉터는 본문을 읽어 지문을 만들고, 끝난 응답의 바이트를 저장해야 하는데 원본 요청 · 응답으로는 둘 다 할 수 없다.
 * 본문을 통째로 메모리에 올리므로 크기를 제한하고, 폼 · 멀티파트는 감싸지 않는다 (원본 스트림을 읽으면 파라미터 파싱이 깨진다).
 * 조건에 맞지 않는 @Idempotent 요청은 인터셉터가 413 / 415로 거절한다.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    // 입금 · 결제 요청 본문은 수백 바이트 수준이다. 그보다 훨씬 큰 값만 막으면 된다.
    static final long MAX_BODY_BYTES = 64 * 1024;

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!shouldWrap(request)) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(new CachedBodyRequestWrapper(request), cachingResponse);
        } finally {
            // 버퍼에 쌓인 본문을 실제 응답으로 흘려보내야 클라이언트가 받는다.
            cachingResponse.copyBodyToResponse();
        }
    }

    /***
     * 함수 기능: 본문 길이를 미리 알 수 없거나(chunked) 상한을 넘는 요청인지 확인한다.
     * @return 감싸지 않을 만큼 큰 요청이면 true
     */
    static boolean isTooLarge(HttpServletRequest request) {
        long length = request.getContentLengthLong();
        return length > MAX_BODY_BYTES || (length < 0 && request.getHeader("Transfer-Encoding") != null);
    }

    private boolean shouldWrap(HttpServletRequest request) {
        return request.getHeader(IdempotencyInterceptor.HEADER) != null
                && WRITE_METHODS.contains(request.getMethod())
                && isJsonOrEmpty(request.getContentType())
                && !isTooLarge(request);
    }

    private boolean isJsonOrEmpty(String contentType) {
        if (contentType == null) {
            return true;
        }
        try {
            return MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType));
        } catch (IllegalArgumentException e) {
            // 깨진 Content-Type은 감싸지 않는다. @Idempotent 요청이면 인터셉터가 415로 거절한다.
            return false;
        }
    }
}
