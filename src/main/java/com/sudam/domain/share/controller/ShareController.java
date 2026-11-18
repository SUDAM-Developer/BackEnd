package com.sudam.domain.share.controller;

import com.sudam.domain.share.dto.request.CreateShareRequest;
import com.sudam.domain.share.dto.response.ShareResponse;
import com.sudam.domain.share.exception.code.ShareSuccessCode;
import com.sudam.domain.share.service.ShareService;
import com.sudam.global.ApiResponse;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "카카오톡 공유", description = "카카오톡 공유 창에 넣을 정보 생성 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shares")
public class ShareController {

    private final ShareService shareService;

    @Operation(summary = "공유 정보 생성",
            description = "카카오톡 공유 창을 열기 직전에 호출한다. 받는 사람의 링크 URL과 메시지 제목 · 설명 · 버튼 문구를 돌려주고 "
                    + "공유 기록을 REQUESTED로 남긴다. 지금은 SETTLEMENT_REPORT(sourceId = 리포트 ID)와 "
                    + "PAYMENT_RESEND(sourceId = 청구 ID)만 지원한다. 보낸 뒤에는 리포트 발송 확인 · 입금 안내 다시 보내기 확인을 호출한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ShareResponse>> create(@AuthenticationPrincipal CustomOAuth2User principal,
                                                             @Valid @RequestBody CreateShareRequest request) {
        ShareResponse response = shareService.create(principal.getUserId(), request);
        return ResponseEntity
                .status(ShareSuccessCode.SHARE_CREATED.getStatus())
                .body(ApiResponse.onSuccess(ShareSuccessCode.SHARE_CREATED, response));
    }
}
