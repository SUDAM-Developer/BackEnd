package com.sudam.domain.report.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.record.entity.SessionProgress;
import com.sudam.domain.record.entity.SessionRecord;
import com.sudam.domain.record.enums.Understanding;
import com.sudam.domain.record.repository.SessionProgressRepository;
import com.sudam.domain.record.repository.SessionRecordRepository;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.entity.SettlementReportProgress;
import com.sudam.domain.report.repository.SettlementReportProgressRepository;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.settlement.port.CycleSessionReader;
import com.sudam.domain.settlement.port.SettlementReportCreator;
import com.sudam.domain.tutoring.entity.Textbook;
import com.sudam.domain.tutoring.entity.TutoringPolicies;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.repository.TutoringPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 정산 실행(SettlementReportCreator 포트)이 부르는 리포트 생성 구현.
 * 방금 닫은 사이클의 기록으로 통계 · 진도 표를 계산해 리포트 행과 진도 행을 저장한다.
 * 정산과 함께 롤백되도록 기록 저장 트랜잭션에 참여만 한다 (MANDATORY).
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class SettlementReportFactory implements SettlementReportCreator {

    private final CycleRepository cycleRepository;
    private final TutoringPolicyRepository tutoringPolicyRepository;
    private final SessionRecordRepository sessionRecordRepository;
    private final SessionProgressRepository sessionProgressRepository;
    private final SettlementReportRepository settlementReportRepository;
    private final SettlementReportProgressRepository settlementReportProgressRepository;
    private final CycleSessionReader cycleSessionReader;

    /***
     * 함수 기능: 방금 닫은 사이클의 정산 리포트(통계 + 교재별 진도 표)를 만든다.
     * @param tutoringId 과외 ID
     * @param cycleId 방금 SETTLED로 바꾼 사이클 ID
     * @return 만든 리포트 ID
     * @throws IllegalStateException 사이클이나 적용할 정책이 없는 경우 (정산 실행이 먼저 확인하므로 데이터 이상)
     */
    @Override
    public Long create(Long tutoringId, Long cycleId) {
        // 1. 사이클 · 정책 조회: 목표 회차는 이 사이클 번호에 적용된 정책의 정산 회차다.
        Cycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new IllegalStateException("사이클이 없습니다. cycleId=" + cycleId));
        int targetCount = TutoringPolicies.applicableTo(
                        tutoringPolicyRepository.findAllByTutoringId(tutoringId), cycle.getCycleNo())
                .map(TutoringPolicy::getSettlementSessionCnt)
                .orElseThrow(() -> new IllegalStateException("적용할 과외 정책이 없습니다. tutoringId=" + tutoringId));

        // 2. 통계 계산: 인정 회차의 기록으로 숙제 수행률과 이해도 횟수를 센다.
        List<SessionRecord> records = sessionRecordRepository.findAllRecognizedInCycle(cycleId);
        Integer homeworkRate = ReportCalculator.homeworkRate(
                records.stream().map(SessionRecord::getPreviousHomeworkResult).toList());

        // 3. 직전 수행률: 직전 사이클 리포트의 값을 복사한다. 첫 사이클이거나 리포트가 없으면 null.
        Integer prevHomeworkRate = cycleRepository.findByTutoringIdAndCycleNo(tutoringId, cycle.getCycleNo() - 1)
                .flatMap(prev -> settlementReportRepository.findByCycleId(prev.getId()))
                .map(SettlementReport::getHomeworkRate)
                .orElse(null);

        // 4. 리포트 저장: 회차 수와 휴강 수는 정산 실행과 같은 기준으로 세도록 회차 조회 포트를 쓴다.
        SettlementReport report = settlementReportRepository.save(SettlementReport.builder()
                .tutoring(cycle.getTutoring())
                .cycle(cycle)
                .completedCount(cycleSessionReader.countRecognized(tutoringId, cycleId))
                .targetCount(targetCount)
                .canceledCount(cycleSessionReader.canceledIn(tutoringId, cycleId).size())
                .homeworkRate(homeworkRate)
                .prevHomeworkRate(prevHomeworkRate)
                .understandingHigh(count(records, Understanding.HIGH))
                .understandingMedium(count(records, Understanding.MEDIUM))
                .understandingLow(count(records, Understanding.LOW))
                .build());

        // 5. 진도 표 저장: 교재별로 사이클 첫 회차의 시작 진도 ~ 마지막 회차의 끝 진도를 라벨로 굳혀 저장한다.
        settlementReportProgressRepository.saveAll(progressRows(report, cycleId));
        return report.getId();
    }

    private int count(List<SessionRecord> records, Understanding understanding) {
        return (int) records.stream().filter(record -> record.getUnderstanding() == understanding).count();
    }

    /***
     * 함수 기능: 사이클의 회차별 진도를 교재마다 한 줄(첫 시작 ~ 마지막 끝)로 접는다. 교재 ID 순으로 정렬한다.
     * @param report 진도 행이 속할 리포트
     * @param cycleId 사이클 ID
     * @return 리포트 진도 행. 진도 기록이 없으면 빈 목록
     */
    private List<SettlementReportProgress> progressRows(SettlementReport report, Long cycleId) {
        // 회차 정렬 순서로 받으므로 교재별 첫 행이 첫 진도, 마지막 행이 마지막 진도다.
        Map<Textbook, List<SessionProgress>> byTextbook = new LinkedHashMap<>();
        for (SessionProgress progress : sessionProgressRepository.findAllRecognizedInCycle(cycleId)) {
            byTextbook.computeIfAbsent(progress.getTextbook(), key -> new ArrayList<>()).add(progress);
        }

        List<Textbook> textbooks = byTextbook.keySet().stream()
                .sorted(Comparator.comparing(Textbook::getId))
                .toList();
        List<SettlementReportProgress> rows = new ArrayList<>();
        for (int order = 0; order < textbooks.size(); order++) {
            Textbook textbook = textbooks.get(order);
            List<SessionProgress> items = byTextbook.get(textbook);
            rows.add(SettlementReportProgress.create(report, textbook,
                    ReportCalculator.progressLabel(textbook, items.getFirst().getStartPosition()),
                    ReportCalculator.progressLabel(textbook, items.getLast().getEndPosition()),
                    order));
        }
        return rows;
    }
}
