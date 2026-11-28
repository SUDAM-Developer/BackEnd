package com.sudam.domain.invoice.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.enums.InvoiceKind;
import com.sudam.domain.invoice.repository.InvoiceRepository;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("FirstInvoiceIssuer")
class FirstInvoiceIssuerTest {

    private static final TeacherProfile TEACHER = TeacherProfile.create(
            User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app"), "한서윤", List.of(Subject.MATH), null);

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private FirstInvoiceIssuer firstInvoiceIssuer;

    @Test
    @DisplayName("첫 청구서를 수업료 금액으로 첫 사이클에 발행해 저장한다")
    void 첫_청구서를_발행한다() {
        // given
        Tutoring tutoring = Tutoring.builder()
                .teacher(TEACHER)
                .subjects(List.of(Subject.MATH))
                .studentName("김서아")
                .gradeLevel(9)
                .gradeSchoolYear(2026)
                .build();
        TutoringPolicy policy = TutoringPolicy.builder()
                .tutoring(tutoring)
                .effectiveCycleNo(1)
                .settlementSessionCnt(8)
                .paymentTiming(PaymentTiming.PREPAID)
                .tuitionFee(400_000)
                .bankCode("004")
                .bankName("국민")
                .accountNumber("123456-01-491492")
                .accountHolder("한서윤")
                .build();
        Cycle cycle = Cycle.open(tutoring, 1, LocalDateTime.of(2026, 11, 26, 18, 0));
        ReflectionTestUtils.setField(cycle, "id", 7L);
        given(invoiceRepository.save(any(Invoice.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Invoice invoice = firstInvoiceIssuer.issue(policy, cycle);

        // then
        assertThat(invoice.getKind()).isEqualTo(InvoiceKind.FIRST);
        assertThat(invoice.getAmount()).isEqualTo(400_000);
        assertThat(invoice.getBilledCycleId()).isEqualTo(7L);
        assertThat(invoice.getReportId()).isNull();
    }
}
