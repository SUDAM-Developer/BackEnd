package com.sudam.global.security.oauth2.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class KakaoResponse implements OAuth2Response {

    private final String providerId;
    private final String email;
    private final String name;

    @Override
    public String getProvider() {
        return "kakao";
    }
}