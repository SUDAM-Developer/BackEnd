package com.sudam.domain.user.dto.request;

import com.sudam.domain.user.enums.TermsCode;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AgreeTermsRequest(

        @NotEmpty(message = "동의한 약관을 선택해 주세요.")
        List<TermsCode> termsCodes
) {}
