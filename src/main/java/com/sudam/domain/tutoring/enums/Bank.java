package com.sudam.domain.tutoring.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 계좌 등록 시 선택 가능한 은행 목록. 표시 이름과 금융결제원 은행 코드.
@Getter
@RequiredArgsConstructor
public enum Bank {
    KOOKMIN("국민", "004"),
    SHINHAN("신한", "088"),
    WOORI("우리", "020"),
    HANA("하나", "081"),
    NONGHYUP("농협", "011"),
    IBK("기업", "003"),
    SC("SC제일", "023"),
    CITI("씨티", "027"),
    KAKAO_BANK("카카오뱅크", "090"),
    TOSS_BANK("토스뱅크", "092"),
    K_BANK("케이뱅크", "089"),
    IM_BANK("iM뱅크", "031"),
    BUSAN("부산", "032"),
    KYONGNAM("경남", "039"),
    GWANGJU("광주", "034"),
    JEONBUK("전북", "037"),
    JEJU("제주", "035"),
    SUHYUP("수협", "007"),
    KDB("산업", "002"),
    SAEMAUL("새마을금고", "045"),
    SHINHYUP("신협", "048"),
    POST_OFFICE("우체국", "071"),
    SAVINGS_BANK("저축은행", "050"),
    SANLIM("산림조합", "064");

    private final String displayName;
    private final String code;
}
