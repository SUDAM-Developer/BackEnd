package com.sudam.global.security.exception;

import com.sudam.global.exception.code.BaseErrorCode;
import com.sudam.global.exception.code.GeneralErrorCode;
import com.sudam.global.exception.ProjectException;
import com.sudam.global.security.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver resolver;

    // HandlerExceptionResolver 빈이 여러 개라 DispatcherServlet의 복합 resolver를 이름으로 지정한다.
    public CustomAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    /***
     * 함수 기능: 인증되지 않은 요청을 공통 응답 포맷으로 돌려준다.
     *          필터가 담아둔 토큰 검증 실패 사유가 있으면 그 코드를, 없으면 COMMON401을 쓴다.
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) {

        Object errorCode = request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE);

        BaseErrorCode resolved = (errorCode instanceof BaseErrorCode code)
                ? code
                : GeneralErrorCode.UNAUTHORIZED;

        resolver.resolveException(request, response, null, new ProjectException(resolved));
    }
}
