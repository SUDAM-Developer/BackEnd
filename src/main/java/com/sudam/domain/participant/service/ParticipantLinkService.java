package com.sudam.domain.participant.service;

import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.ParticipantRole;
import com.sudam.domain.participant.exception.ParticipantException;
import com.sudam.domain.participant.exception.code.ParticipantErrorCode;
import com.sudam.domain.participant.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 링크 토큰으로 참여자를 찾는다 (ERD 9.1.1 링크 열기 순서의 앞부분). 링크 토큰이 곧 열쇠라 로그인 없이 쓴다.
 * 첫 입장 수신 동의(9.1.2) · 없는 토큰 반복 요청 제한은 아직 하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipantLinkService {

    private final ParticipantRepository participantRepository;
    private final Clock clock;

    /***
     * 함수 기능: 링크 토큰으로 학부모 참여자를 찾는다. 과외 · 선생님을 함께 가져온다.
     * @param linkToken 링크 토큰 원문
     * @return 링크가 유효한 학부모
     * @throws ParticipantException 없는 토큰, 삭제된 참여자 · 과외, 만료된 링크, 학부모가 아니면 PARTICIPANT404_1
     */
    public Participant resolveParent(String linkToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        return participantRepository.findWithTutoringByLinkToken(linkToken)
                .filter(participant -> participant.getRemovedAt() == null)
                .filter(participant -> !participant.getTutoring().isDeleted())
                // 진행 중인 과외는 만료가 없고(NULL), 수업을 마치면 종료일 + 30일까지만 열린다.
                .filter(participant -> participant.getLinkExpiresAt() == null
                        || participant.getLinkExpiresAt().isAfter(now))
                .filter(participant -> participant.getRole() == ParticipantRole.PARENT)
                .orElseThrow(() -> new ParticipantException(ParticipantErrorCode.LINK_NOT_FOUND));
    }
}
