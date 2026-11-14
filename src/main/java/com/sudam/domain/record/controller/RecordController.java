package com.sudam.domain.record.controller;

import com.sudam.domain.record.dto.request.SaveRecordRequest;
import com.sudam.domain.record.dto.response.SaveRecordResponse;
import com.sudam.domain.record.exception.code.RecordSuccessCode;
import com.sudam.domain.record.service.RecordService;
import com.sudam.global.ApiResponse;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "수업 기록", description = "수업 기록 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tutorings/{tutoringId}/records")
public class RecordController {

    private final RecordService recordService;

    @Operation(summary = "수업 기록 저장",
            description = "반복 일정의 수업 한 회차를 기록하고 사이클 진행 현황을 돌려준다. "
                    + "이미 기록한 수업이면 RECORD409_1. 정산은 하지 않아 settlement는 항상 정산 전이다.")
    @PostMapping
    public ResponseEntity<ApiResponse<SaveRecordResponse>> save(@AuthenticationPrincipal CustomOAuth2User principal,
                                                               @PathVariable Long tutoringId,
                                                               @Valid @RequestBody SaveRecordRequest request) {
        SaveRecordResponse response = recordService.save(principal.getUserId(), tutoringId, request);
        return ResponseEntity
                .status(RecordSuccessCode.RECORD_SAVED.getStatus())
                .body(ApiResponse.onSuccess(RecordSuccessCode.RECORD_SAVED, response));
    }
}
