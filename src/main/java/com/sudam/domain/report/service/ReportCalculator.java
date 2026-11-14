package com.sudam.domain.report.service;

import com.sudam.domain.record.enums.HomeworkResult;
import com.sudam.domain.tutoring.entity.Textbook;

import java.util.List;
import java.util.Objects;

/**
 * 리포트 통계 · 진도 라벨 계산. DB 없이 값만으로 계산하는 순수 함수라 단위 테스트로 바로 검증한다.
 */
public final class ReportCalculator {

    private ReportCalculator() {
    }

    /***
     * 함수 기능: 숙제 수행률(%)을 계산한다 = (DONE + PARTIAL × 0.5) ÷ 결과가 있는 기록 수, 반올림.
     *          첫 수업처럼 지난 숙제 결과가 없는 기록(null)은 분모에서 뺀다.
     * @param results 사이클 기록들의 지난 숙제 결과 (null 포함 가능)
     * @return 수행률 0 ~ 100. 결과가 하나도 없으면 null
     */
    public static Integer homeworkRate(List<HomeworkResult> results) {
        List<HomeworkResult> present = results.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        // 0.5점을 정수로 다루려고 DONE = 2, PARTIAL = 1점으로 센 뒤 분모도 2배로 한다.
        int points = present.stream()
                .mapToInt(result -> switch (result) {
                    case DONE -> 2;
                    case PARTIAL -> 1;
                    case NOT_DONE -> 0;
                })
                .sum();
        return (int) Math.round(points * 100.0 / (present.size() * 2));
    }

    /***
     * 함수 기능: 교재 단위에 맞춰 진도 숫자를 표시 문자열로 만든다.
     *          UNIT "Unit 12", CHAPTER "Chapter 3", SECTION "3단원", PAGE "45p", ROUND "3회", CUSTOM "9강"(숫자 + 직접 입력한 단위).
     * @param textbook 교재 (진도 단위 · 직접 입력 단위)
     * @param position 진도 숫자
     * @return 진도 라벨
     */
    public static String progressLabel(Textbook textbook, int position) {
        return switch (textbook.getProgressUnit()) {
            case UNIT -> "Unit " + position;
            case CHAPTER -> "Chapter " + position;
            case SECTION -> position + "단원";
            case PAGE -> position + "p";
            case ROUND -> position + "회";
            case CUSTOM -> position + textbook.getCustomUnitLabel();
        };
    }
}
