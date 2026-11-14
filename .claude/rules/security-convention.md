---
description: 인증/인가 규칙 — OAuth2 로그인 체인, JWT 발급·검증, principal, 소셜 공급자 추가, 허용 URI 변경
paths:
  - "src/main/java/com/sudam/global/security/**"
  - "src/main/java/com/sudam/global/config/SecurityConfig.java"
  - "src/main/resources/application*.yaml"
---

# 보안 / 인증 컨벤션

## 현재 구성

`SecurityConfig`의 단일 `SecurityFilterChain`:

- `csrf`, `httpBasic`, `formLogin` 비활성화. 세션은 `SessionCreationPolicy.STATELESS`.
- CORS는 `corsConfigurationSource` 빈으로 켠다. 허용 출처는 프로파일별 `app.cors.allowed-origins`(개발 서버는 `FRONT_BASE_URL` + 로컬 프론트)에서 읽고, RT 쿠키를 주고받아야 해서 자격 증명을 허용한다(출처에 `*` 불가). 허용 헤더는 `Authorization` · `Content-Type`.
- `allowUris`(`/oauth2/**`, `/login/**`, `/api/v1/auth/reissue`, Swagger 경로 `/swagger-ui/**`·`/swagger-ui.html`·`/v3/api-docs/**`)만 `permitAll`, 나머지는 `authenticated`. 재발급은 AT가 만료된 상태로 호출되므로 인증을 걸 수 없고, 인증 주체가 RT 쿠키다.
- `oauth2Login`에 `CustomOAuth2UserService`(userInfo) + `OAuth2LoginSuccessHandler` + `OAuth2LoginFailureHandler`를 연결.
- `JwtAuthenticationFilter`를 `UsernamePasswordAuthenticationFilter` 앞에 등록(빈이 아니라 `new`로 만들어 `JwtUtil`·`TokenStore`를 넘긴다). `exceptionHandling`에 `CustomAuthenticationEntryPoint`(401) + `CustomAccessDeniedHandler`(403) 연결.

흐름(로그인): 카카오 인가 → `CustomOAuth2UserService.loadUser`가 attributes를 `OAuth2Response`로 정규화 → `UserService.findOrCreate`로 회원 조회·가입 → `CustomOAuth2User`(principal) 생성 → `OAuth2LoginSuccessHandler`가 AT·RT를 발급하고 **RT를 `TokenStore`에 저장**한 뒤, AT는 URI 프래그먼트, RT는 `CookieUtil`의 HttpOnly 쿠키로 실어 프론트로 리다이렉트.

흐름(인증 요청): `JwtAuthenticationFilter`가 `Bearer` AT를 `JwtUtil.parseAccessToken`으로 검증(서명·만료·`typ=access`)하고 `TokenStore.isBlacklisted(jti)`로 차단 여부를 확인한 뒤 `SecurityContext`에 `CustomOAuth2User`를 채운다. 검증 실패 시 사유만 request attribute에 담고 통과시키면 `AuthorizationFilter`가 거부하고 EntryPoint가 `handlerExceptionResolver`로 응답을 만든다.

흐름(재발급): `AuthService.reissue`가 RT를 검증(`typ=refresh`)하고 `rt:{userId}`의 저장값과 **문자열로 비교**한다. 없으면 `AUTH401_5`, 다르면 탈취로 보고 저장값을 삭제하고 `AUTH401_6`, 같으면 새 AT·RT를 발급하고 저장값을 덮어쓴다(RTR).

흐름(로그아웃·탈퇴): `AuthService.logout`이 `rt:{userId}`를 삭제하고 AT의 `jti`를 남은 만료 시간만큼 `bl:{jti}`에 올린다. 탈퇴는 `UserService.withdraw`(DB 논리 삭제)에 이 처리를 덧붙인 것이다.

## 토큰 설계

| | 클레임 | 만료 | 전달 | 서버 상태 |
| --- | --- | --- | --- | --- |
| AT | `jti`, `sub`, `role`, `typ=access` | 30분 | `Authorization: Bearer` | 차단 목록 `bl:{jti}` |
| RT | `jti`, `sub`, `role`, `typ=refresh` | 14일 | `refreshToken` HttpOnly 쿠키 | `rt:{userId}` (회원당 1개) |

- **`typ` 클레임을 반드시 확인한다.** `parseAccessToken` / `parseRefreshToken`으로 갈라져 있고 공용 `parse`는 private이다. 종류 검증을 건너뛰면 AT를 RT 쿠키에 넣은 요청이 "저장값 불일치"로 오판되어 **정상 RT가 삭제**된다.
- **RT에도 `jti`가 필요하다.** `iat`·`exp`가 초 단위로 직렬화되므로 `jti`가 없으면 같은 초에 재발급한 RT가 이전 RT와 글자까지 같아져 재사용 감지가 뚫린다.
- RT에 `role`을 담는 이유는 재발급이 DB를 조회하지 않게 하기 위해서다. 역할 변경은 재로그인 때 반영된다(현재 `role`은 가입 시 TEACHER 고정).
- 차단 목록 TTL은 토큰의 남은 만료 시간(`JwtUtil.getRemainingExpiration`)으로 잡아 목록이 무한히 커지지 않게 한다.
- Redis 키 접두사는 `TokenStore` 안에만 둔다. 다른 클래스가 `"rt:"` 같은 문자열을 직접 만들지 않는다.

## 지켜야 할 것

- 인증 principal은 **항상 `CustomOAuth2User`**. 내부에는 `userId`와 `Role`만 담고, 소셜 attributes를 그대로 들고 다니지 않는다(`getAttributes()`는 의도적으로 빈 Map).
- 권한 문자열은 `"ROLE_" + Role.name()` 규칙을 유지한다. 새 권한이 필요하면 `com.sudam.domain.user.enums.Role`에 상수를 추가한다.
- 엔드포인트를 공개하려면 `SecurityConfig.allowUris` 배열에 추가한다. 메서드 레벨 `@PreAuthorize`를 쓰려면 `@EnableMethodSecurity`를 먼저 켠다.
- 세션이 STATELESS이므로 로그인 상태를 세션/쿠키에 보관하지 않는다. 인증 상태는 토큰으로 전달한다.
- 인증 예외는 흐름에 따라 둘로 나눈다 (에러 코드는 `AuthErrorCode` 공용). 소셜 로그인은 `SocialLoginException`(`OAuth2AuthenticationException` 상속, `global/security/oauth2/exception/`) — `OAuth2LoginAuthenticationFilter`가 그 자리에서 낚아채 `OAuth2LoginFailureHandler`로 보낸다. JWT 검증은 `AuthException`(`ProjectException` 상속, `domain/auth/exception/`)을 쓴다.
- **인증 코드 enum은 `domain/auth/exception/code/`에 있다** (`AuthErrorCode`, `AuthSuccessCode`). 코드는 도메인 자산이므로 `global/security`에 두지 않는다 — 여기에는 필터·토큰 저장소·유틸·OAuth2 배선만 남긴다. 상세는 `error-code-convention.md`.
- 필터에서 던진 예외는 `@RestControllerAdvice`가 잡지 못한다. `JwtAuthenticationFilter`는 `AuthException`을 catch해 에러 코드를 request attribute에 담고 체인을 통과시키고, `CustomAuthenticationEntryPoint`가 그 값을 꺼내 `handlerExceptionResolver`에 위임한다.
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

- RT 쿠키는 `SameSite=Lax` 고정이고 `Secure`만 프로파일별 `app.cookie.secure`를 따른다(로컬 false, 개발 서버 true). 프론트(`sudam.app`)와 API(`api.sudam.app`)가 같은 사이트라 Lax로 충분하다. 프론트를 **다른 사이트**(다른 등록 도메인)로 옮기면 `sameSite("None")`으로 바꿔야 한다.
- 다중 기기 로그인을 지원하지 않는다. `rt:{userId}`가 회원당 1개라 두 번째 로그인이 첫 번째 세션의 RT를 무효화한다. 기기별로 두려면 키를 `rt:{userId}:{deviceId}`로 바꿔야 한다.
- 로그아웃·탈퇴는 **요청에 쓰인 AT 하나만** 차단한다. 다른 기기의 AT가 살아 있다면 만료(최대 30분)까지 유효하다.
- AT 없이 RT 쿠키만으로 로그아웃하는 경로가 없다. AT가 만료됐으면 재발급 후 로그아웃해야 한다.
- 탈퇴 시 카카오 연결 끊기(unlink)를 호출하지 않는다 (ERD 1.2.3). 넣을 때는 외부 API 호출이므로 트랜잭션 밖에서 처리한다.
- 탈퇴가 `users` 행만 정리한다. 연락처가 담긴 다른 테이블(`tutoring_drafts` 등)은 해당 도메인이 생길 때 함께 처리한다.
- 프론트 출처가 늘거나 바뀌면 `SecurityConfig`가 아니라 프로파일 yaml의 `app.cors.allowed-origins`를 고친다. 새 요청 헤더를 쓰게 되면 `corsConfigurationSource`의 허용 헤더에 추가한다.
