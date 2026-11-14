package com.sudam.domain.record.dto.request;

import com.sudam.domain.record.enums.HomeworkResult;
import com.sudam.domain.record.enums.Understanding;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record SaveRecordRequest(

        @NotNull(message = "수업 일정을 선택해 주세요.")
        Long scheduleId,

        // 요일 · 적용 기간 · 미래 날짜 검사는 일정과 오늘 날짜가 필요해 서비스에서 한다.
        @NotNull(message = "수업 날짜를 입력해 주세요.")
        LocalDate originalDate,

        @NotEmpty(message = "교재 진도를 하나 이상 입력해 주세요.")
        @Size(max = 5, message = "교재는 5개까지 기록할 수 있습니다.")
        List<@Valid ProgressRequest> progress,

        @NotNull(message = "이해도를 선택해 주세요.")
        Understanding understanding,

        HomeworkResult previousHomeworkResult,

        @Size(max = 100, message = "다음 숙제는 100자 이내로 입력해 주세요.")
        String nextHomework,

        @Size(max = 100, message = "관찰 메모는 100자 이내로 입력해 주세요.")
        String observationMemo
) {

    public record ProgressRequest(

            @NotNull(message = "교재를 선택해 주세요.")
            Long textbookId,

            // 시작 ≤ 끝 검사는 전용 코드(RECORD400_3)가 있어 서비스에서 한다.
            @NotNull(message = "시작 진도를 입력해 주세요.")
            @Min(value = 0, message = "진도는 0 이상이어야 합니다.")
            Integer startPosition,

            @NotNull(message = "끝 진도를 입력해 주세요.")
            @Min(value = 0, message = "진도는 0 이상이어야 합니다.")
            Integer endPosition
    ) {
    }

    // 같은 교재가 두 줄이면 session_progress 유니크 제약에서 500이 나기 전에 400으로 막는다.
    @AssertTrue(message = "같은 교재를 두 번 입력할 수 없습니다.")
    public boolean isTextbookIdsUnique() {
        if (progress == null) {
            return true;
        }
        return progress.stream()
                .filter(Objects::nonNull)
                .map(ProgressRequest::textbookId)
                .distinct()
                .count() == progress.size();
    }
}
