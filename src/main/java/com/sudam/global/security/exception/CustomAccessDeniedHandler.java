package com.sudam.global.security.exception;

import com.sudam.global.code.GeneralErrorCode;
import com.sudam.global.exception.ProjectException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public CustomAccessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    /***
     * 함수 기능: 인증은 됐지만 권한이 부족한 요청을 공통 응답 포맷으로 돌려준다.
     */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {

        resolver.resolveException(request, response, null, new ProjectException(GeneralErrorCode.FORBIDDEN));
    }
}
