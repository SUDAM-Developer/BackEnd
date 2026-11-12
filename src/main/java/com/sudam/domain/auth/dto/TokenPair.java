package com.sudam.domain.auth.dto;

/***
 * 재발급한 토큰 묶음. 액세스 토큰은 응답 본문, 리프레시 토큰은 쿠키로 나가므로
 * 서비스가 둘을 함께 돌려주고 컨트롤러가 각자 자리에 넣는다.
 */
public record TokenPair(String accessToken, String refreshToken) {
}
