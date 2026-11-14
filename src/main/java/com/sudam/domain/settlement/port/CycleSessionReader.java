package com.sudam.domain.settlement.port;

import com.sudam.domain.invoice.entity.CanceledLesson;

import java.util.List;

/**
 * 정산 실행이 회차 숫자를 읽는 창구. 회차(sessions) · 기록은 김강민 담당이라 구현도 그쪽에 둔다 (11/13 · 11/14 합의).
 *
 * <p>계약 (구현이 지킬 것):
 * <ul>
 *     <li>정산 실행(SettlementService.settleIfReached)이 기록 저장 트랜잭션 안에서, 과외 행을 잠근 뒤 부른다.
 *         방금 저장한 기록이 보이도록 같은 트랜잭션에서 읽는다 (새 트랜잭션을 열지 않는다).</li>
 *     <li>읽기만 한다. 회차 · 사이클을 바꾸지 않는다.</li>
 *     <li>정렬 기준은 ERD의 session_date → start_time(NULL은 뒤) → id.</li>
 * </ul>
 */
public interface CycleSessionReader {

    /***
     * 함수 기능: 사이클의 인정 회차 수를 센다 = cycle_id가 이 사이클이고 is_recognized = TRUE인 회차 수
     *          (기록 완료 + 인정 휴강). P0는 당일 취소 인정이 없어 기록 완료 수와 같다.
     * @param tutoringId 과외 ID
     * @param cycleId    사이클 ID
     * @return 인정 회차 수. 없으면 0
     */
    int countRecognized(Long tutoringId, Long cycleId);

    /***
     * 함수 기능: 사이클 기간을 구한다 = 이 사이클 인정 회차의 MIN ~ MAX(session_date).
     *          정산 실행은 정산 회차를 채운 뒤에만 부르므로 인정 회차가 1개 이상 있다.
     * @param tutoringId 과외 ID
     * @param cycleId    사이클 ID
     * @return 기간 (start ≤ end)
     */
    CyclePeriod period(Long tutoringId, Long cycleId);

    /***
     * 함수 기능: 사이클 기간 안의 휴강 내역을 날짜순으로 돌려준다 = status = CANCELED인 회차 (인정 · 미인정 모두).
     *          미인정 휴강은 cycle_id가 NULL이라 소속을 날짜로 판단한다
     *          (직전 정산 사이클의 마지막 인정 날짜 다음 날 ~ 이 사이클의 마지막 인정 날짜).
     *          청구 스냅샷 canceledLessons에 그대로 들어간다.
     * @param tutoringId 과외 ID
     * @param cycleId    사이클 ID
     * @return 휴강 내역. cancelReason은 sessions.cancel_reason의 enum 이름 문자열(STUDENT_SAME_DAY 등).
     *         없으면 빈 목록 (NULL 아님)
     */
    List<CanceledLesson> canceledIn(Long tutoringId, Long cycleId);
}
