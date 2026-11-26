package com.sudam.domain.record.repository;

/**
 * 교재 1권의 진도 행 하나에서 끝 진도만 꺼낸 값.
 */
public record TextbookLastPosition(

        Long textbookId, // 교재 ID

        Integer endPosition // 그 회차의 끝 진도
) {}
