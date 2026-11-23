package com.sudam.domain.schedule.enums;

/**
 * 일정 탭 회차 카드 오른쪽 버튼 (5.1.1 · 5.2.4).
 */
public enum LessonAction {
    RECORD, // [기록하기] 오늘 · 지난 미기록 정규 회차
    ADJUST, // [조율] 앞으로의 정규 예정 회차
    NONE // 버튼 없음: 기록됨 · 휴강 · 보강
}
