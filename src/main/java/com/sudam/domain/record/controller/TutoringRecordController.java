package com.sudam.domain.record.controller;

import com.sudam.domain.record.dto.request.SaveRecordRequest;
import com.sudam.domain.record.dto.response.RecordFormResponse;
import com.sudam.domain.record.dto.response.SaveRecordResponse;
import com.sudam.domain.record.exception.code.RecordSuccessCode;
import com.sudam.domain.record.service.RecordFormService;
import com.sudam.domain.record.service.TutoringRecordService;
import com.sudam.global.ApiResponse;
import com.sudam.global.security.oauth2.CustomOAuth2User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "수업 기록", description = "수업 기록 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tutorings/{tutoringId}")
public class TutoringRecordController {

    private final TutoringRecordService tutoringRecordService;
    private final RecordFormService recordFormService;

    @Operation(summary = "회차 기록 화면 조회",
            description = "저장 전 반복 일정 회차(scheduleId + originalDate)의 기록 화면 정보를 돌려준다. "
                    + "지난 진도 · 숙제는 이 회차 날짜보다 앞선 기록 기준이고, 앞선 기록이 없는 교재는 교재의 시작 진도를 준다. "
                    + "이미 저장된 회차(기록 · 휴강 · 시간 변경)면 RECORD409_1.")
    @GetMapping("/record-form")
    public ApiResponse<RecordFormResponse> getRecordForm(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @PathVariable Long tutoringId,
            @RequestParam Long scheduleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate originalDate) {
        return ApiResponse.onSuccess(RecordSuccessCode.RECORD_FORM_FOUND,
                recordFormService.getRecordForm(principal.getUserId(), tutoringId, scheduleId, originalDate));
    }

    @Operation(summary = "수업 기록 저장",
            description = "반복 일정의 수업 한 회차를 기록하고 사이클 진행 현황을 돌려준다. "
                    + "이미 기록한 수업이면 RECORD409_1. 인정 회차가 정산 회차에 닿으면 같은 트랜잭션에서 정산하고 settlement에 사이클 · 리포트 · 청구 ID를 담는다.")
    @PostMapping("/records")
    public ResponseEntity<ApiResponse<SaveRecordResponse>> save(@AuthenticationPrincipal CustomOAuth2User principal,
                                                               @PathVariable Long tutoringId,
                                                               @Valid @RequestBody SaveRecordRequest request) {
        SaveRecordResponse response = tutoringRecordService.save(principal.getUserId(), tutoringId, request);
        return ResponseEntity
                .status(RecordSuccessCode.RECORD_SAVED.getStatus())
                .body(ApiResponse.onSuccess(RecordSuccessCode.RECORD_SAVED, response));
    }
}
