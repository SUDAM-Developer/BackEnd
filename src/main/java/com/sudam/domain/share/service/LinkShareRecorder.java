package com.sudam.domain.share.service;

import com.sudam.domain.share.enums.ShareKind;
import com.sudam.domain.share.enums.ShareSentVia;
import com.sudam.domain.share.enums.ShareSourceType;
import com.sudam.domain.share.enums.ShareStatus;
import com.sudam.domain.share.repository.LinkShareRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 보낸 것이 확인된 공유를 SENT로 남긴다. 웹훅을 받기 전(UT)에는 선생님 확인 API
 * (리포트 발송 확인 · 입금 안내 다시 보내기 확인)가 원인 데이터를 바꾸는 같은 트랜잭션에서 부른다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class LinkShareRecorder {

    private final LinkShareRepository linkShareRepository;

    /***
     * 함수 기능: 원인 데이터의 그 종류 REQUESTED 공유를 모두 SENT(MANUAL)로 바꾼다.
     *          공유 창을 열지 않고 확인만 눌렀으면 바꿀 행이 없다 (원인 데이터는 그대로 바뀐다).
     * @param kind       공유 종류 (같은 청구의 첫 안내와 다시 안내를 구분한다)
     * @param sourceType 원인 데이터 종류
     * @param sourceId   원인 데이터 ID
     * @param sentAt     보낸 것으로 확인한 시각
     * @return SENT로 바꾼 공유 수
     */
    public int markSentByTeacher(ShareKind kind, ShareSourceType sourceType, Long sourceId, LocalDateTime sentAt) {
        return linkShareRepository.markRequestedSent(kind, sourceType, sourceId, ShareStatus.SENT,
                ShareSentVia.MANUAL, sentAt);
    }
}
