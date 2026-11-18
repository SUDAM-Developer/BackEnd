package com.sudam.domain.report.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 정산 리포트 코멘트 저장(확인) 요청. 필드 이름은 노션 명세의 text를 따른다.
 *
 * @param text 발송될 코멘트
 */
public record SaveReportCommentRequest(
        @NotBlank(message = "코멘트를 입력해 주세요.")
        @Size(max = 1000, message = "코멘트는 1000자 이하로 입력해 주세요.")
        String text
) {
}
