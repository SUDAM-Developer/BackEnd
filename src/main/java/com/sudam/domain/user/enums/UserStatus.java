package com.sudam.domain.user.enums;

// 온보딩 진행 상태.
public enum UserStatus {
    TERMS_REQUIRED, // 약관 동의 필요
    PROFILE_REQUIRED, // 프로필 등록 필요
    ACTIVE, // 온보딩 완료
}
