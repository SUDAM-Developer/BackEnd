package com.sudam.domain.tutoring.service;

import com.sudam.domain.tutoring.enums.Bank;
import java.util.Arrays;
import java.util.Optional;

// 은행 이름 문자열을 지원 은행 목록에서 찾는 상태 없는 유틸리티.
public final class BankCatalog {

    private static final String BANK_SUFFIX = "은행";

    private BankCatalog() {
    }

    // 모든 공백을 제거하고 끝의 '은행' 하나를 제거한 뒤, 같은 정규화를 거친 표시 이름과 비교한다.
    public static Optional<Bank> find(String bankName) {
        if (bankName == null) {
            return Optional.empty();
        }
        String normalized = normalize(bankName);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(Bank.values())
                .filter(bank -> normalize(bank.getDisplayName()).equals(normalized))
                .findFirst();
    }

    private static String normalize(String name) {
        String stripped = name.replaceAll("\s+", "");
        return stripped.endsWith(BANK_SUFFIX)
                ? stripped.substring(0, stripped.length() - BANK_SUFFIX.length())
                : stripped;
    }
}
