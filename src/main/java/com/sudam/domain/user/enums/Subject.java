package com.sudam.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Subject {

    ENGLISH("영어"),
    MATH("수학"),
    KOREAN("국어"),
    SCIENCE("과학"),
    SOCIAL("사회"),
    OTHER("기타"),
    ;

    private final String name;
}
