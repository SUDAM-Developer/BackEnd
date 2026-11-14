package com.sudam.domain.invoice.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * 청구 스냅샷(invoices.snapshot)의 휴강 내역 한 줄.
 * cancelReason은 sessions.cancel_reason의 enum 이름(STUDENT_SAME_DAY 등)을 그대로 담는다.
 * 회차 엔티티가 아직 없어 문자열로 두고, 저장된 JSON 키가 바뀌지 않도록 이름을 고정한다.
 *
 * @param date         휴강 날짜. Jackson 2 · 3 어느 쪽이 매핑해도 "yyyy-MM-dd" 문자열로 저장되게 형식을 고정한다
 * @param cancelReason 휴강 사유
 */
public record CanceledLesson(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate date,
        String cancelReason
) {
}
