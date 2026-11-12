# API 명세

> 최종 수정: 2026-11-11
> 현재 구현된 REST 엔드포인트는 없다. Spring Security OAuth2 Client가 제공하는 로그인 경로만 열려 있다.
> 작성 규칙은 `.claude/rules/api-convention.md`, 코드 체계는 `.claude/rules/error-code-convention.md`를 따른다.

## 공통

- Base URL(로컬): `http://localhost:8080`
- 모든 엔드포인트 prefix: `/api/v1`
- 요청/응답 Content-Type: `application/json;charset=UTF-8`
- `/oauth2/**`, `/login/**`을 제외한 모든 경로는 인증이 필요하다 (`SecurityConfig.allowUris`).

### 공통 응답 형식

모든 응답은 `ApiResponse`로 감싸진다. 키 순서는 고정이다.

```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": { }
}
```

실패:

```json
{
  "isSuccess": false,
  "code": "COMMON400",
  "message": "잘못된 요청입니다.",
  "result": {
    "nickname": "닉네임은 필수입니다."
  }
}
```

`result`는 검증 실패 시 `{필드명: 메시지}` Map이고, 그 외 실패에서는 `null`이다.

### 공통 성공 코드

| code | HTTP | message |
| --- | --- | --- |
| `COMMON200` | 200 | 성공적으로 요청을 처리했습니다. |
| `COMMON201` | 201 | 성공적으로 생성이 되었습니다. |

### 공통 에러 코드

| code | HTTP | message |
| --- | --- | --- |
| `COMMON400` | 400 | 잘못된 요청입니다. |
| `COMMON401` | 401 | 인증되지 않았습니다. |
| `COMMON403` | 403 | 접근이 금지되었습니다. |
| `COMMON404` | 404 | 해당 리소스를 찾을 수 없습니다. |
| `COMMON405` | 405 | 지원하지 않는 HTTP 메서드입니다. |
| `COMMON406` | 406 | 응답할 수 있는 표현 형식이 없습니다. |
| `COMMON409` | 409 | 다른 요청이 먼저 처리되었습니다. 다시 시도해 주세요. |
| `COMMON413` | 413 | 요청 본문이 허용된 크기를 초과했습니다. |
| `COMMON415` | 415 | 지원하지 않는 Content-Type입니다. |
| `COMMON500` | 500 | 서버 측 문제입니다. |
| `COMMON503` | 503 | 일시적으로 요청을 처리할 수 없습니다. |

### 도메인 에러 코드

**인증 (`AuthErrorCode`)**

| code | HTTP | message |
| --- | --- | --- |
| `AUTH400_1` | 400 | 지원하지 않는 소셜 로그인입니다. |
| `AUTH401_1` | 401 | 필수 동의 항목에 동의해야 로그인할 수 있습니다. |
| `AUTH401_2` | 401 | 소셜 로그인에 실패했습니다. |
| `AUTH401_3` | 401 | 유효하지 않은 토큰입니다. |

<!-- TODO: 도메인이 추가되면 에러 코드 표를 도메인별로 덧붙인다. -->

---

## 인증

### 카카오 로그인 시작

| | |
| --- | --- |
| Method | `GET` |
| Path | `/oauth2/authorization/kakao` |
| 인증 | 불필요 |

Spring Security가 제공하는 경로. 카카오 인가 페이지로 리다이렉트한다.

### 카카오 콜백 (리다이렉트 URI)

| | |
| --- | --- |
| Method | `GET` |
| Path | `/login/oauth2/code/kakao` |
| 인증 | 불필요 |

카카오가 호출하는 경로. 성공 시 `OAuth2LoginSuccessHandler`, 실패 시 `OAuth2LoginFailureHandler`로 이어진다.
리다이렉트라 응답 본문을 쓸 수 없어 AT는 프래그먼트, RT는 `HttpOnly` 쿠키로 전달한다. 프론트 URI는 `app.oauth2.success-redirect-uri` / `app.oauth2.failure-redirect-uri`(프로파일별 설정)에서 읽는다.

- 성공: `302 {프론트 성공 URI}#accessToken={AT}` + `Set-Cookie: refreshToken={RT}; Path=/; Max-Age=1209600; HttpOnly; SameSite=Lax`
- 실패: `302 {프론트 실패 URI}#code={에러 코드}` (예: `#code=AUTH401_1`)

필수 동의 항목: `profile_nickname`, `account_email`. 누락 시 `AUTH401_1`.

### 인증이 필요한 요청

`Authorization: Bearer {AT}` 헤더로 보낸다. `JwtAuthenticationFilter`가 서명·만료를 검증해 `SecurityContext`에 `CustomOAuth2User`를 채운다. 만료는 AT 30분 / RT 14일(`jwt.*` 설정).

| 상황 | HTTP | code |
| --- | --- | --- |
| 토큰 없음 | 401 | `COMMON401` |
| 만료·위조된 토큰, RT를 AT 자리에 보낸 경우 | 401 | `AUTH401_3` |
| 인증은 됐지만 권한 부족 | 403 | `COMMON403` |

공개 경로(`/oauth2/**`, `/login/**`)는 만료된 토큰을 함께 보내도 그대로 통과한다.

<!-- TODO: AT 재발급·로그아웃 엔드포인트는 다음 이슈에서 추가한다. -->

---

## <도메인명>

<!--
새 엔드포인트는 아래 틀로 추가한다.

### <기능 이름>

| | |
| --- | --- |
| Method | `POST` |
| Path | `/api/v1/<자원>` |
| 인증 | 필요 (`ROLE_USER`) |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `nickname` | string | O | 1~30자 | 닉네임 |

**Response** (`201`, `COMMON201`)

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | number | 생성된 자원 ID |

**에러**

| code | HTTP | 상황 |
| --- | --- | --- |
| `COMMON400` | 400 | 요청 검증 실패 |
-->