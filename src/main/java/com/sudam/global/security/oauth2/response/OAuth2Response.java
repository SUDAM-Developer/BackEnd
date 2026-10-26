package com.sudam.global.security.oauth2.response;

public interface OAuth2Response {

    String getProvider(); // 공급자 식별자 ex) kakao, naver, google
    String getProviderId(); // 공급자가 부여한 회원 식별자
    String getName(); // 회원 이름
    String getEmail(); // 회원 이메일
}
