package com.sudam.domain.user.dto.request;

import com.sudam.domain.user.enums.Subject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveProfileRequest(

        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(min = 2, max = 10, message = "이름은 2~10자로 입력해 주세요.")
        String name,

        @NotEmpty(message = "과목을 하나 이상 선택해 주세요.")
        List<Subject> subjects,

        // OTHER를 골랐을 때만 필수. 과목에 따라 달라지는 규칙이라 서비스에서 검사한다.
        @Size(max = 20, message = "기타 과목명은 20자 이내로 입력해 주세요.")
        String subjectOther
) {
}
