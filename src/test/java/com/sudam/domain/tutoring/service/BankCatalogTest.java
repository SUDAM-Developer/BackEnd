package com.sudam.domain.tutoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sudam.domain.tutoring.enums.Bank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BankCatalogTest {

    @ParameterizedTest
    @ValueSource(strings = {"국민", "국민은행", " 국 민 은행 ", "국민\t은행", "국민\u00A0은행"})
    @DisplayName("은행 이름은 공백과 끝의 '은행'을 무시하고 찾는다")
    void 정규화_후_국민을_찾는다(String input) {
        // given / when
        Bank bank = BankCatalog.find(input).orElseThrow();

        // then
        assertThat(bank).isEqualTo(Bank.KOOKMIN);
        assertThat(bank.getCode()).isEqualTo("004");
    }

    @Test
    @DisplayName("카카오뱅크는 090")
    void 카카오뱅크_코드() {
        // given / when / then
        assertThat(BankCatalog.find("카카오뱅크").orElseThrow().getCode()).isEqualTo("090");
    }

    @Test
    @DisplayName("우리은행은 020")
    void 우리은행_코드() {
        // given / when / then
        assertThat(BankCatalog.find("우리은행").orElseThrow().getCode()).isEqualTo("020");
    }

    @ParameterizedTest
    @ValueSource(strings = {"저축", "저축은행", "iM뱅크", "SC제일은행", "새마을금고"})
    @DisplayName("표시 이름에 '은행'이 붙은 은행도 입력과 같은 정규화로 찾는다")
    void 표시_이름_정규화(String input) {
        // given / when / then
        assertThat(BankCatalog.find(input)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"KB", "기타", ""})
    @DisplayName("목록에 없는 이름은 빈 값")
    void 없는_이름은_empty(String input) {
        // given / when / then
        assertThat(BankCatalog.find(input)).isEmpty();
    }

    @Test
    @DisplayName("null 입력은 빈 값")
    void null은_empty() {
        // given / when / then
        assertThat(BankCatalog.find(null)).isEmpty();
    }

    @Test
    @DisplayName("모든 은행은 자신의 표시 이름으로 찾아진다")
    void 모든_은행_조회() {
        // given / when / then
        for (Bank bank : Bank.values()) {
            assertThat(BankCatalog.find(bank.getDisplayName())).contains(bank);
        }
    }
}
