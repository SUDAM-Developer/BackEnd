package com.sudam.domain.participant.repository;

import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.ParticipantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    // 링크 토큰으로 참여자를 찾는다. 링크 화면이 과외 삭제 여부 · 선생님 이름을 쓰므로 과외 · 선생님을 함께 가져온다.
    @Query("""
            select p from Participant p join fetch p.tutoring t join fetch t.teacher
            where p.linkToken = :linkToken
            """)
    Optional<Participant> findWithTutoringByLinkToken(@Param("linkToken") String linkToken);

    // 과외에서 링크를 받을 수 있는 참여자: 삭제되지 않았고 링크 토큰이 있는 사람. 등록 순.
    @Query("""
            select p from Participant p
            where p.tutoring.id = :tutoringId and p.role = :role
              and p.removedAt is null and p.linkToken is not null
            order by p.id
            """)
    List<Participant> findLinkHolders(@Param("tutoringId") Long tutoringId, @Param("role") ParticipantRole role);
}
