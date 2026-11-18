package com.sudam.domain.share.repository;

import com.sudam.domain.share.entity.LinkShare;
import com.sudam.domain.share.enums.ShareKind;
import com.sudam.domain.share.enums.ShareSentVia;
import com.sudam.domain.share.enums.ShareSourceType;
import com.sudam.domain.share.enums.ShareStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LinkShareRepository extends JpaRepository<LinkShare, Long> {

    // 같은 원인 · 같은 받는 사람의 이전 REQUESTED를 DISMISSED로 바꾼다 (ERD: 공유 창을 여러 번 열어도 할 일은 1건).
    @Modifying
    @Query("""
            update LinkShare s set s.status = com.sudam.domain.share.enums.ShareStatus.DISMISSED
            where s.sourceType = :sourceType and s.sourceId = :sourceId and s.participant.id = :participantId
              and s.status = com.sudam.domain.share.enums.ShareStatus.REQUESTED
            """)
    int dismissRequested(@Param("sourceType") ShareSourceType sourceType,
                         @Param("sourceId") Long sourceId,
                         @Param("participantId") Long participantId);

    // 원인 데이터의 그 종류 REQUESTED 공유를 모두 SENT로 바꾼다. 리포트 발송 확인 · 입금 안내 다시 보내기 확인이 부른다.
    @Modifying
    @Query("""
            update LinkShare s set s.status = :sent, s.sentVia = :sentVia, s.sentAt = :sentAt
            where s.kind = :kind and s.sourceType = :sourceType and s.sourceId = :sourceId
              and s.status = com.sudam.domain.share.enums.ShareStatus.REQUESTED
            """)
    int markRequestedSent(@Param("kind") ShareKind kind,
                          @Param("sourceType") ShareSourceType sourceType,
                          @Param("sourceId") Long sourceId,
                          @Param("sent") ShareStatus sent,
                          @Param("sentVia") ShareSentVia sentVia,
                          @Param("sentAt") LocalDateTime sentAt);
}
