package com.sudam.domain.participant.repository;

import com.sudam.domain.participant.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    // 링크 토큰으로 참여자를 찾는다. 링크 화면이 과외 삭제 여부 · 선생님 이름을 쓰므로 과외 · 선생님을 함께 가져온다.
    @Query("""
            select p from Participant p join fetch p.tutoring t join fetch t.teacher
            where p.linkToken = :linkToken
            """)
    Optional<Participant> findWithTutoringByLinkToken(@Param("linkToken") String linkToken);

    // 과외들의 카톡 공유를 받을 수 있는 학부모(삭제 안 됨 · 링크 토큰 있음)를 한 번에 모은다. 과외 순 · 등록 순.
    // 리포트 상세 · 입금 현황이 공유 정보 생성(POST /shares)의 받는 사람으로 내려준다.
    @Query("""
            select p from Participant p
            where p.tutoring.id in :tutoringIds
              and p.role = com.sudam.domain.participant.enums.ParticipantRole.PARENT
              and p.removedAt is null and p.linkToken is not null
            order by p.tutoring.id, p.id
            """)
    List<Participant> findShareableParents(@Param("tutoringIds") Collection<Long> tutoringIds);
}
