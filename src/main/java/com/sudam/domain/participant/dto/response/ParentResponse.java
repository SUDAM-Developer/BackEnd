package com.sudam.domain.participant.dto.response;

import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.ParticipantLabel;

/**
 * 카카오톡으로 받을 수 있는 학부모 한 명. 프론트가 공유 정보 생성(POST /shares)의 participantId로 쓴다.
 *
 * @param participantId 학부모 참여자 ID
 * @param label         호칭 (MOTHER 어머님 / FATHER 아버님 / GUARDIAN 보호자님)
 */
public record ParentResponse(Long participantId, ParticipantLabel label) {

    public static ParentResponse from(Participant parent) {
        return new ParentResponse(parent.getId(), parent.getLabel());
    }
}
