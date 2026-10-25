package com.sudam.global.handler;

import com.sudam.global.ApiResponse;
import com.sudam.global.code.BaseErrorCode;
import com.sudam.global.code.GeneralErrorCode;
import com.sudam.global.exception.ProjectException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GeneralExceptionHandler extends ResponseEntityExceptionHandler {

    // 스프링이 정해준 상태 코드를 공통 에러 코드로 되돌리기 위한 표
    // ex) 400: BAD_REQUEST, 404: NOT_FOUND
    private static final Map<Integer, BaseErrorCode> ERROR_CODE_BY_STATUS = Arrays.stream(GeneralErrorCode.values())
            .collect(Collectors.toUnmodifiableMap(
                    errorCode -> errorCode.getStatus().value(),
                    errorCode -> errorCode));

    // 프로젝트 전용 예외 처리
    @ExceptionHandler(ProjectException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectException(ProjectException e){

        BaseErrorCode errorCode = e.getErrorCode();
        log.warn("[프로젝트 지정 예외] code={}, message={}", errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.onFailure(errorCode));
    }

    // @ModelAttribute의 타입 변환 실패 혹은 검증 실패, @RequestBody의 검증 실패
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request){

        // 타입 변환 or 검증 실패한 변수명과 실패 이유를 담을 Map
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach((error) -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        BaseErrorCode errorCode = resolveErrorCode(status);
        log.warn("[요청 DTO 지정 예외] code={}, message={}", errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity.status(status).headers(headers)
                .body(ApiResponse.onFailure(errorCode, errors));
    }

    // @PathVariable, @RequestParam (단일 파라미터)의 타입 변환 실패
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e,
                                                        HttpHeaders headers,
                                                        HttpStatusCode status,
                                                        WebRequest request){

        // 타입 변환 실패한 경로 변수 or 요청 파라미터명과 실패 이유를 담을 Map
        String name = (e instanceof MethodArgumentTypeMismatchException typeMismatch)
                ? typeMismatch.getName()
                : e.getPropertyName();
        Map<String, String> errors = new HashMap<>();
        errors.put(Objects.requireNonNullElse(name, "unknown"), "타입이 올바르지 않습니다.");

        BaseErrorCode errorCode = resolveErrorCode(status);
        log.warn("[요청 단일 파라미터 타입 예외] code={}, message={}", errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity.status(status).headers(headers)
                .body(ApiResponse.onFailure(errorCode, errors));
    }

    // @PathVariable, @RequestParam (단일 파라미터)의 검증 실패
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request){

        // 검증 실패한 파라미터명과 실패 이유를 담을 Map
        Map<String, String> errors = new HashMap<>();
        e.getParameterValidationResults().forEach((error) -> {
            errors.put(error.getMethodParameter().getParameterName(), error.getResolvableErrors().getFirst().getDefaultMessage());
        });

        BaseErrorCode errorCode = resolveErrorCode(status);
        log.warn("[요청 단일 파라미터 검증 예외] code={}, message={}", errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity.status(status).headers(headers)
                .body(ApiResponse.onFailure(errorCode, errors));
    }

    // 위에서 잡지 않은 스프링 표준 예외(깨진 JSON, 405, 415, 404, 필수 파라미터 누락 등)는 여기서 잡힌다.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e,
                                                             @Nullable Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode status,
                                                             WebRequest request){

        BaseErrorCode errorCode = resolveErrorCode(status);

        // 500대 에러는 ERROR 레벨이다.
        if (status.is5xxServerError()) {
            log.error("[스프링 표준 예외] code={}", errorCode.getCode(), e);
        } else {
            log.warn("[스프링 표준 예외] code={}, type={}, message={}",
                    errorCode.getCode(), e.getClass().getSimpleName(), e.getMessage());
        }

        return ResponseEntity.status(status).headers(headers)
                .body(ApiResponse.onFailure(errorCode));
    }

    // 그 외 지정되지 않은 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(Exception e){

        BaseErrorCode errorCode = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        log.error("[미지정 예외] code={}", errorCode.getCode(), e);

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.onFailure(errorCode));
    }

    // statusCode에 맞는 에러코드를 Map에서 찾는다.
    private BaseErrorCode resolveErrorCode(HttpStatusCode statusCode){
        return ERROR_CODE_BY_STATUS.getOrDefault(statusCode.value(), GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
}
