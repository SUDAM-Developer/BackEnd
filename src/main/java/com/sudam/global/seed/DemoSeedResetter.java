package com.sudam.global.seed;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 시드 선생님의 과외와 그 아래 데이터를 지운다 (sudam.seed.reset = true로 재기동할 때, UT 리허설 뒤 처음 상태로 되돌리기).
 * 도메인 레포지토리에 시드 전용 삭제 메서드를 늘리지 않으려고 JPQL 일괄 삭제를 여기 모아 둔다.
 * User · TeacherProfile · 약관 동의는 지우지 않는다 (카카오 로그인 상태를 유지하기 위해서).
 * 시드 생성과 같은 트랜잭션에서만 돈다 (MANDATORY). 다시 만들다 실패하면 삭제까지 롤백되어 원래 데이터가 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "sudam.seed", name = "enabled", havingValue = "true")
@Transactional(propagation = Propagation.MANDATORY)
public class DemoSeedResetter {

    private static final String INVOICE_IDS = "(select i.id from Invoice i where i.tutoring.id in :ids)";
    private static final String REPORT_IDS = "(select r.id from SettlementReport r where r.tutoring.id in :ids)";
    private static final String SESSION_IDS = "(select s.id from Session s where s.tutoring.id in :ids)";

    // FK 역순 (자식 → 부모). 테이블이 늘면 여기에 추가한다 (예: settlement_report_views는 SettlementReport 앞).
    private static final List<String> STATEMENTS = List.of(
            "delete from InvoiceAmountAdjustment a where a.invoice.id in " + INVOICE_IDS,
            "delete from InvoicePayment p where p.invoice.id in " + INVOICE_IDS,
            "delete from Invoice i where i.tutoring.id in :ids",
            "delete from SettlementReportProgress p where p.report.id in " + REPORT_IDS,
            "delete from SettlementReport r where r.tutoring.id in :ids",
            "delete from SessionProgress p where p.session.id in " + SESSION_IDS,
            "delete from SessionRecord r where r.session.id in " + SESSION_IDS,
            "delete from Session s where s.tutoring.id in :ids",
            "delete from Cycle c where c.tutoring.id in :ids",
            // 학생 행이 같은 테이블의 학부모 행(guardian_consent_by)을 가리키므로, 한 번에 지우기 전에 참조를 비운다.
            "update Participant p set p.guardianConsentBy = null where p.tutoring.id in :ids",
            "delete from Participant p where p.tutoring.id in :ids",
            "delete from TutoringSetting s where s.tutoring.id in :ids",
            "delete from TutoringPolicy p where p.tutoring.id in :ids",
            "delete from Textbook t where t.tutoring.id in :ids",
            "delete from TutoringSchedule s where s.tutoring.id in :ids",
            "delete from Tutoring t where t.id in :ids"
    );

    private final EntityManager entityManager;

    /***
     * 함수 기능: 선생님의 과외 전부와 그 아래 데이터(일정 · 교재 · 정책 · 설정 · 참여자 · 사이클 · 회차 · 기록 · 진도 · 리포트 · 청구)를 지운다.
     *          일괄 삭제는 영속성 컨텍스트를 거치지 않는다. 시드는 이 호출 전에 과외 쪽 엔티티를 불러오지 않으므로
     *          낡은 엔티티가 남지 않는다 (불러온 것은 지우지 않는 User뿐).
     * @param teacherId 시드 선생님 회원 ID
     * @return 지운 과외 수 (0이면 아무것도 지우지 않았다)
     */
    public int deleteTutoringsOf(Long teacherId) {
        List<Long> tutoringIds = entityManager
                .createQuery("select t.id from Tutoring t where t.teacher.id = :teacherId", Long.class)
                .setParameter("teacherId", teacherId)
                .getResultList();
        if (tutoringIds.isEmpty()) {
            return 0;
        }

        for (String jpql : STATEMENTS) {
            entityManager.createQuery(jpql).setParameter("ids", tutoringIds).executeUpdate();
        }
        log.warn("[가상 데이터] 리셋: 시드 선생님의 과외 {}개와 그 아래 데이터를 지웠습니다. teacherId={}, tutoringIds={}",
                tutoringIds.size(), teacherId, tutoringIds);
        return tutoringIds.size();
    }
}
