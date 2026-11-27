package com.sudam.domain.participant.service;

import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.repository.ParticipantRepository;
import com.sudam.domain.tutoring.entity.Tutoring;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 과외 생성 시 학부모 · 학생 참여자를 만들고 링크 토큰을 발급하는 창구.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ParticipantInviter {

    private final ParticipantRepository participantRepository;
    private final LinkTokenGenerator linkTokenGenerator;

    /***
     * 함수 기능: 학부모(입력 순서) → 학생(번호가 있을 때만) 순으로 참여자를 만들고 각각 링크 토큰을 발급해 저장한다.
     * @param tutoring           저장된 과외
     * @param parents            학부모 연락처 (정규화된 번호)
     * @param studentPhoneOrNull 학생 번호, 없으면 null
     * @param now                토큰 발급 시각
     * @return 저장된 참여자 (학부모 → 학생 순서)
     */
    public List<Participant> invite(Tutoring tutoring, List<ParentContact> parents, String studentPhoneOrNull,
                                    LocalDateTime now) {
        List<Participant> participants = new ArrayList<>();
        for (ParentContact parent : parents) {
            participants.add(Participant.createParent(tutoring, parent.label(), parent.phone()));
        }
        if (studentPhoneOrNull != null) {
            participants.add(Participant.createStudent(tutoring, studentPhoneOrNull));
        }
        participants.forEach(participant -> participant.issueLinkToken(linkTokenGenerator.generate(), now));
        return participantRepository.saveAll(participants);
    }
}
