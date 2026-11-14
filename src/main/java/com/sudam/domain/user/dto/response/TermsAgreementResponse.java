package com.sudam.domain.user.dto.response;

import com.sudam.domain.user.entity.UserTermsAgreement;
import com.sudam.domain.user.enums.TermsCode;

import java.time.LocalDateTime;
import java.util.List;

public record TermsAgreementResponse(

        List<Item> agreements // 사용자 동의 항목 리스트
){

    public record Item(TermsCode code, LocalDateTime agreedAt) {

        public static Item from(UserTermsAgreement agreement) {
            return new Item(agreement.getTerms().getCode(), agreement.getAgreedAt());
        }
    }

    public static TermsAgreementResponse from(List<UserTermsAgreement> agreements) {
        return new TermsAgreementResponse(agreements.stream().map(Item::from).toList());
    }
}
