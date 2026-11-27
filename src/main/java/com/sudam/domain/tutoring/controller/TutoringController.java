package com.sudam.domain.tutoring.controller;

import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest;
import com.sudam.domain.tutoring.dto.response.TutoringCreateResponse;
import com.sudam.domain.tutoring.exception.code.TutoringSuccessCode;
import com.sudam.domain.tutoring.service.TutoringCreateService;
import com.sudam.global.ApiResponse;
import com.sudam.global.idempotency.Idempotent;
import com.sudam.global.idempotency.IdempotencyInterceptor;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "과외", description = "과외 생성 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tutorings")
public class TutoringController {

    private final TutoringCreateService tutoringCreateService;

    @Operation(summary = "과외 만들기",
            description = "선생님이 학생 · 교재 · 일정 · 정산 · 계좌 · 알림 · 초대 정보를 한 번에 보내 과외를 만든다. "
                    + "선불이면 첫 청구서를 함께 발행하고, 참여자별 링크를 돌려준다.")
    @Parameter(name = IdempotencyInterceptor.HEADER, in = ParameterIn.HEADER, required = true,
            description = "중복 생성을 막는 멱등 키(UUID). 재시도에는 같은 값을 보낸다.")
    @Idempotent
    @PostMapping
    public ResponseEntity<ApiResponse<TutoringCreateResponse>> create(@AuthenticationPrincipal CustomOAuth2User principal,
                                                                      @Valid @RequestBody TutoringCreateRequest request) {
        TutoringCreateResponse response = tutoringCreateService.create(principal.getUserId(), request);
        return ResponseEntity
                .status(TutoringSuccessCode.CREATED.getStatus())
                .body(ApiResponse.onSuccess(TutoringSuccessCode.CREATED, response));
    }
}
