---
description: 인증/인가 규칙 — OAuth2 로그인 체인, principal, 소셜 공급자 추가, 허용 URI 변경
paths:
  - "src/main/java/com/sudam/global/security/**"
  - "src/main/resources/application*.yaml"
---

# 보안 / 인증 컨벤션

## 현재 구성

`SecurityConfig`의 단일 `SecurityFilterChain`:

- `csrf`, `httpBasic`, `formLogin` 비활성화. 세션은 `SessionCreationPolicy.STATELESS`.
- `allowUris`(`/oauth2/**`, `/login/**`)만 `permitAll`, 나머지는 `authenticated`.
- `oauth2Login`에 `CustomOAuth2UserService`(userInfo) + `OAuth2LoginSuccessHandler` + `OAuth2LoginFailureHandler`를 연결.

흐름: 카카오 인가 → `CustomOAuth2UserService.loadUser`가 attributes를 `OAuth2Response`로 정규화 → `CustomOAuth2User`(principal) 생성 → 성공/실패 핸들러.

## 지켜야 할 것

- 인증 principal은 **항상 `CustomOAuth2User`**. 내부에는 `userId`와 `Role`만 담고, 소셜 attributes를 그대로 들고 다니지 않는다(`getAttributes()`는 의도적으로 빈 Map).
- 권한 문자열은 `"ROLE_" + Role.name()` 규칙을 유지한다. 새 권한이 필요하면 `com.sudam.domain.user.enums.Role`에 상수를 추가한다.
- 엔드포인트를 공개하려면 `SecurityConfig.allowUris` 배열에 추가한다. 메서드 레벨 `@PreAuthorize`를 쓰려면 `@EnableMethodSecurity`를 먼저 켠다.
- 세션이 STATELESS이므로 로그인 상태를 세션/쿠키에 보관하지 않는다. 인증 상태는 토큰으로 전달한다.
- 인증 흐름에서 던지는 예외는 `AuthException`(+`AuthErrorCode`)만 쓴다. `ProjectException`은 `@RestControllerAdvice`가 잡는 MVC 영역용이라 **필터 단계에서는 잡히지 않는다.**
- 실패 응답 포맷을 바꿀 때는 `OAuth2LoginFailureHandler`에서 `ApiResponse.onFailure(errorCode)`를 직접 직렬화해야 한다. 예외 핸들러를 거치지 않는다.
- 민감 값(`KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, DB 비밀번호)은 전부 `.env`에서 읽는다. yaml이나 코드에 하드코딩하지 않고, 로그로도 출력하지 않는다.
- 소셜 응답을 로그로 남길 때 providerId/email처럼 식별 가능한 값은 디버깅에 필요한 최소한으로 줄인다. `log.info`로 남기는 개인정보는 운영 전환 시 재검토 대상이다.

## 소셜 공급자를 추가할 때

1. `application.yaml`의 `spring.security.oauth2.client.registration` / `provider`에 등록 정보를 추가하고, 비밀값은 `.env` + `.env.example`에 키를 넣는다.
2. 리다이렉트 URI는 프로파일별 파일(`application-local.yaml` 등)에 둔다.
3. `oauth2/response`에 `OAuth2Response` 구현체(`NaverResponse` 등)를 추가한다 — `provider`는 하드코딩 문자열, 나머지는 필드.
4. `CustomOAuth2UserService`의 `switch (registrationId)`에 분기와 `to<Provider>Response` 메서드를 추가한다. 응답 키는 클래스 상단 `private static final String KEY_*` 상수로 선언한다.
5. 필수 동의 항목(이메일·닉네임)이 누락될 수 있다는 전제로 파싱한다. 동의하지 않은 항목은 **키 자체가 응답에서 빠진다** — 중첩 Map은 `asMap`처럼 방어적으로 꺼내고, 누락 시 `AuthErrorCode.REQUIRED_CONSENT_MISSING`으로 중단한다.
6. 지원하지 않는 `registrationId`는 `AuthErrorCode.UNSUPPORTED_PROVIDER`로 막는다 (default 분기 유지).

## 남아 있는 TODO (건드릴 때 함께 처리)

- `CustomOAuth2UserService.loadUser`가 `new CustomOAuth2User(1L, Role.USER)`를 하드코딩으로 반환한다. 회원 조회/저장 연결이 필요하다.
- `OAuth2LoginSuccessHandler`는 JWT 발급·리다이렉트가, `OAuth2LoginFailureHandler`는 에러 리다이렉트가 미구현이다. 현재는 로그만 남긴다.