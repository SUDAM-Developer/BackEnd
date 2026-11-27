package com.sudam.domain.tutoring.service;

import com.sudam.domain.tutoring.enums.ParentUpdateFrequency;
import com.sudam.domain.tutoring.enums.PresetType;

// 온보딩 답변으로 공유 설정 프리셋을 판정하는 상태 없는 유틸리티.
// 유형은 학부모 근황 주기로만 정하며 숙제 알림은 영향을 주지 않는다.
public final class PresetJudge {

    private PresetJudge() {
    }

    // [건너뛰기]는 답을 무시하고 SIMPLE. skipped=false인데 답이 null이면 호출자(서비스)가 먼저 검증해야 한다.
    public static PresetType judge(boolean skipped, ParentUpdateFrequency parentAnswer) {
        if (skipped) {
            return PresetType.SIMPLE;
        }
        return switch (parentAnswer) {
            case EVERY_SESSION -> PresetType.THOROUGH;
            case AT_SETTLEMENT -> PresetType.SIMPLE;
            case NONE -> PresetType.MINIMAL;
        };
    }
}
