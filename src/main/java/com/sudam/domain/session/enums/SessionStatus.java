package com.sudam.domain.session.enums;

public enum SessionStatus {
    SCHEDULED, // 보강 예정과 시간을 변경한 회차만 저장된다. (정상 예정 회차는 아직 저장되지 않기에)
    COMPLETED, // 과외 기록 완료
    CANCELED // 과외 휴강
}
