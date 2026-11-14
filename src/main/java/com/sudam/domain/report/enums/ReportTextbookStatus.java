package com.sudam.domain.report.enums;

// 리포트 진도 표의 교재 표기. 사이클 중간에 추가 · 사용 종료된 교재를 구분해 보여준다.
public enum ReportTextbookStatus {
    ACTIVE, // 사이클 내내 사용
    ADDED, // 사이클 중간에 추가 (교재 추가 기능 이후)
    ENDED // 사이클 중간에 사용 종료 (교재 종료 기능 이후)
}
