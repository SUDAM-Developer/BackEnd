package com.sudam.domain.report.dto.response;

import com.sudam.domain.participant.enums.ParticipantLabel;

import java.util.List;

/**
 * 정산 리포트를 받을 학부모 목록. 프론트가 카카오톡 공유 메시지에 받는 사람별 링크를 넣는 데 쓴다.
 *
 * @param items 받는 사람 (등록 순)
 */
public record ReportRecipientListResponse(List<Item> items) {

    /**
     * @param participantId 학부모 참여자 ID
     * @param label         호칭 (MOTHER / FATHER / GUARDIAN)
     * @param shareUrl      학부모 리포트 화면 URL
     */
    public record Item(Long participantId, ParticipantLabel label, String shareUrl) {
    }
}
