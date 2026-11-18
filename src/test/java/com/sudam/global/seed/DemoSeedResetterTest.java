package com.sudam.global.seed;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoSeedResetterTest {

    @Mock private EntityManager entityManager;
    @Mock private TypedQuery<Long> tutoringIdQuery;
    @Mock private Query bulkQuery;

    @InjectMocks
    private DemoSeedResetter demoSeedResetter;

    @Test
    @DisplayName("시드 선생님에게 과외가 없으면 아무것도 지우지 않는다")
    void 과외가_없으면_삭제_안_함() {
        // given
        givenTutoringIds(List.of());

        // when
        int deleted = demoSeedResetter.deleteTutoringsOf(1L);

        // then
        assertThat(deleted).isZero();
        verify(entityManager, never()).createQuery(anyString());
    }

    @Test
    @DisplayName("과외가 있으면 자식 테이블부터 FK 역순으로 지우고, 지운 과외 수를 돌려준다")
    void 자식부터_역순_삭제() {
        // given
        givenTutoringIds(List.of(10L, 11L));
        given(entityManager.createQuery(anyString())).willReturn(bulkQuery);
        given(bulkQuery.setParameter(eq("ids"), eq(List.of(10L, 11L)))).willReturn(bulkQuery);

        // when
        int deleted = demoSeedResetter.deleteTutoringsOf(1L);

        // then
        assertThat(deleted).isEqualTo(2);
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.atLeastOnce()).createQuery(jpql.capture());
        assertThat(jpql.getAllValues()).extracting(DemoSeedResetterTest::targetEntity).containsExactly(
                "InvoiceAmountAdjustment", "InvoicePayment", "Invoice",
                "SettlementReportView", "SettlementReportProgress", "SettlementReport",
                "SessionProgress", "SessionRecord", "Session", "Cycle",
                "Participant", "Participant",
                "TutoringSetting", "TutoringPolicy", "Textbook", "TutoringSchedule", "Tutoring");
        // 학생 행이 같은 테이블의 학부모 행을 가리키므로, 지우기 전에 그 참조를 먼저 비운다.
        assertThat(jpql.getAllValues().get(10)).startsWith("update Participant");
        assertThat(jpql.getAllValues().get(11)).startsWith("delete from Participant");
    }

    private void givenTutoringIds(List<Long> ids) {
        given(entityManager.createQuery(anyString(), eq(Long.class))).willReturn(tutoringIdQuery);
        given(tutoringIdQuery.setParameter(eq("teacherId"), eq(1L))).willReturn(tutoringIdQuery);
        given(tutoringIdQuery.getResultList()).willReturn(ids);
    }

    // "delete from X x ..." / "update X x ..."에서 X를 꺼낸다.
    private static String targetEntity(String jpql) {
        String[] words = jpql.trim().split("\\s+");
        return words[0].equals("delete") ? words[2] : words[1];
    }
}
