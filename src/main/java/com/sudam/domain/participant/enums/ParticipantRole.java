package com.sudam.domain.participant.enums;

// 과외 안에서의 역할. 계정 역할(user.enums.Role)과 별개다.
public enum ParticipantRole {
    PARENT, // 학부모 (과외당 최대 2명)
    STUDENT // 학생 (과외당 1명)
}
