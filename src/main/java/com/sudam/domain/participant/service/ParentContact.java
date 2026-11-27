package com.sudam.domain.participant.service;

import com.sudam.domain.participant.enums.ParticipantLabel;

/**
 * 초대할 학부모 연락처. 번호는 호출 전에 정규화된 11자리, 라벨이 null이면 GUARDIAN으로 만든다.
 */
public record ParentContact(ParticipantLabel label, String phone) {
}
