package com.sudam.domain.invoice.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.invoice.entity.Invoice;
import com.sudam.domain.invoice.entity.InvoiceSnapshot;
import com.sudam.domain.invoice.enums.InvoiceKind;
import com.sudam.domain.invoice.repository.InvoiceRepository;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 과외 생성 시 첫 사이클의 첫 청구서를 발행하는 창구.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class FirstInvoiceIssuer {

    private final InvoiceRepository invoiceRepository;

    /***
     * 함수 기능: 첫 사이클에 FIRST 청구서를 수업료 그대로 발행해 저장한다. (인정 회차 · 시작일은 아직 없어 null)
     * @param policy 첫 사이클 정책
     * @param cycle  방금 연 첫 사이클
     * @return 저장된 청구서
     */
    public Invoice issue(TutoringPolicy policy, Cycle cycle) {
        InvoiceSnapshot snapshot = InvoiceSnapshot.of(policy, null, null, List.of());
        return invoiceRepository.save(
                Invoice.issue(policy, cycle.getId(), null, InvoiceKind.FIRST, policy.getTuitionFee(), snapshot));
    }
}
