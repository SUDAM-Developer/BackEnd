package com.sudam.domain.user.dto.response;

import com.sudam.domain.user.enums.Role;
import com.sudam.domain.user.enums.UserStatus;

public record MeResponse(

        Role role, // 역할 (현재는 Teacher 고정)
        String name, // 이름
        UserStatus status // 온보딩 진행 상태 (PROFILE_REQUIRED, TERMS_REQUIRED, ACTIVE)
){
}
