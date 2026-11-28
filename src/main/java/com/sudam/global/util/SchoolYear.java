package com.sudam.global.util;

import java.time.LocalDate;

public final class SchoolYear {

    private SchoolYear() {
    }

    /***
     * 함수 기능: 학년도를 구한다. 한국 학년도는 3월에 시작하므로 1 · 2월은 전년도다.
     * @param today 기준일
     * @return 학년도
     */
    public static int of(LocalDate today) {
        return today.getMonthValue() >= 3 ? today.getYear() : today.getYear() - 1;
    }
}
