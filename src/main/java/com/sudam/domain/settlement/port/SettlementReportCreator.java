package com.sudam.domain.settlement.port;

/**
 * 정산 실행이 정산 리포트를 만드는 창구. 정산 리포트는 김강민 담당이라 구현도 그쪽에 둔다 (11/13 · 11/14 합의).
 *
 * <p>계약 (구현이 지킬 것):
 * <ul>
 *     <li>정산 실행이 기록 저장 트랜잭션 안에서, 사이클을 SETTLED로 바꾸고 다음 사이클을 연 뒤, 청구를 만들기 전에 부른다.
 *         같은 트랜잭션에서 실행한다 (새 트랜잭션을 열지 않는다).</li>
 *     <li>리포트가 청구보다 먼저 생기므로 리포트는 청구 ID를 갖지 않는다. 청구가 invoices.report_id로 리포트를 가리키고,
 *         리포트 쪽은 invoices.report_id로 청구를 찾는다.</li>
 *     <li>실패하면 예외를 던진다. 정산 실행은 잡지 않으므로 기록 저장까지 전부 롤백된다.</li>
 *     <li>인공지능 초안 · 알림 같은 느린 작업은 여기서 하지 않는다 (커밋 후 비동기, 이번 범위 밖).</li>
 * </ul>
 */
public interface SettlementReportCreator {

    /***
     * 함수 기능: 방금 닫은 사이클의 정산 리포트 행(통계 · 진도 표, ai_status = PENDING)을 만든다.
     * @param tutoringId 과외 ID
     * @param cycleId    방금 SETTLED로 바꾼 사이클 ID
     * @return 만든 정산 리포트 ID (NULL 아님)
     */
    Long create(Long tutoringId, Long cycleId);
}
