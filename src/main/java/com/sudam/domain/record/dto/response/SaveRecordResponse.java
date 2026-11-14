package com.sudam.domain.record.dto.response;

/**
 * 수업 기록 저장 응답. 저장한 회차와, 그 회차가 속한 사이클의 진행 현황 · 정산 결과를 담는다.
 */
public record SaveRecordResponse(

        // 이번 기록으로 만든 회차(sessions) ID
        Long sessionId,

        // 이 회차가 사이클 안에서 몇 번째 인정 회차인지 (1부터). 지난 수업을 늦게 기록하면 마지막 번호가 아닐 수 있다
        int noInCycle,

        // 회차가 속한 진행 중 사이클의 현황
        CycleSummary cycle,

        // 이번 기록으로 정산이 실행됐는지와 그 결과
        Settlement settlement
) {

    public record CycleSummary(

            // 사이클 번호 (과외 안에서 1부터)
            int cycleNo,

            // 지금까지 이 사이클에 쌓인 인정 회차 수 (이번 기록 포함)
            int recognizedCount,

            // 이 사이클의 정산 회차. 인정 회차 수가 이 값에 닿으면 정산 대상이다
            int settlementSessionCount
    ) {
    }

    public record Settlement(

            // 정산 실행 여부. 정산 기능 전까지는 항상 false
            boolean settled,

            // 정산된 사이클 ID. 정산하지 않았으면 null
            Long cycleId,

            // 정산하며 만든 정산 리포트 ID. 정산하지 않았으면 null
            Long reportId,

            // 정산하며 만든 교육비 청구 ID. 정산하지 않았으면 null
            Long invoiceId
    ) {
    }

    /***
     * 함수 기능: 정산하지 않은 기록 저장 응답을 만든다. settlement는 settled = false, 나머지 ID는 null로 채운다.
     * @param sessionId 저장한 회차 ID
     * @param noInCycle 사이클 안 번호
     * @param cycleNo 사이클 번호
     * @param recognizedCount 사이클의 인정 회차 수
     * @param settlementSessionCount 사이클의 정산 회차
     * @return 기록 저장 응답
     */
    public static SaveRecordResponse beforeSettlement(Long sessionId, int noInCycle, int cycleNo,
                                                      int recognizedCount, int settlementSessionCount) {
        return new SaveRecordResponse(
                sessionId,
                noInCycle,
                new CycleSummary(cycleNo, recognizedCount, settlementSessionCount),
                new Settlement(false, null, null, null)
        );
    }
}
