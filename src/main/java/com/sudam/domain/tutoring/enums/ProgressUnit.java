package com.sudam.domain.tutoring.enums;

// 교재 진도 단위. 모든 단위에서 진도는 숫자 범위로 저장하고 '단위 + 숫자'로 표시한다 (2.1.2).
public enum ProgressUnit {
    UNIT, // Unit
    SECTION, // 단원
    CHAPTER, // Chapter
    PAGE, // 페이지
    ROUND, // 회
    CUSTOM // 기타 (custom_unit_label에 단위 이름)
}
