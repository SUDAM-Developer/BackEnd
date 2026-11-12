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

### 도메인 성공 코드

컨트롤러는 `GeneralSuccessCode`가 아니라 도메인별 성공 코드로 응답한다 (`.claude/rules/error-code-convention.md`).

**인증 (`AuthSuccessCode`)**

| code | HTTP | message |
| --- | --- | --- |
| `AUTH200_1` | 200 | 토큰을 재발급했습니다. |
| `AUTH200_2` | 200 | 로그아웃되었습니다. |

**사용자 (`UserSuccessCode`)**

| code | HTTP | message |
| --- | --- | --- |
| `USER200_1` | 200 | 회원 탈퇴가 완료되었습니다. |

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
| `AUTH401_4` | 401 | 로그아웃된 토큰입니다. 다시 로그인해 주세요. |
| `AUTH401_5` | 401 | 다시 로그인해 주세요. |
| `AUTH401_6` | 401 | 다시 로그인해 주세요. |

`AUTH401_5`(저장된 RT 없음)와 `AUTH401_6`(RT 불일치 — 탈취 의심)은 메시지가 같고 코드로만 구분한다. 클라이언트는 둘 다 로그인 화면으로 보내면 된다.

**사용자 (`UserErrorCode`)**

| code | HTTP | message |
| --- | --- | --- |
| `USER404_1` | 404 | 해당 회원을 찾을 수 없습니다. |

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
| 로그아웃·탈퇴로 차단된 토큰 | 401 | `AUTH401_4` |
| 인증은 됐지만 권한 부족 | 403 | `COMMON403` |

필터는 서명·만료와 `typ=access`를 확인한 뒤, 토큰의 `jti`가 Redis 차단 목록(`bl:{jti}`)에 있는지 본다. `AUTH401_4`를 받으면 **재발급해도 실패하므로** 바로 로그인 화면으로 보낸다 (`AUTH401_3`과 달리 재시도 대상이 아니다).

공개 경로(`/oauth2/**`, `/login/**`, `/api/v1/auth/reissue`)는 만료된 토큰을 함께 보내도 그대로 통과한다.

### 토큰 재발급

| | |
| --- | --- |
| Method | `POST` |
| Path | `/api/v1/auth/reissue` |
| 인증 | 불필요 (`refreshToken` 쿠키로 판단) |

AT가 만료됐을 때 RT로 AT·RT를 함께 재발급한다(RTR). 호출 시점에 AT가 이미 만료돼 있으므로 공개 경로다.

**Request** — 본문 없음. `Cookie: refreshToken={RT}`

**Response** (`200`, `AUTH200_1`)

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `accessToken` | string | 새 액세스 토큰 |

새 RT는 `Set-Cookie: refreshToken={RT}; Path=/; Max-Age=1209600; HttpOnly; SameSite=Lax`로 내려간다.
AT를 본문에 담는 것은 재발급 응답에만 허용한 예외다 — 리다이렉트가 아니라 본문이 유일한 전달 경로다.

**에러**

| 상황 | HTTP | code |
| --- | --- | --- |
| 쿠키 누락, 서명·만료 실패, `typ≠refresh` | 401 | `AUTH401_3` |
| Redis에 `rt:{userId}`가 없음 (로그아웃·탈퇴·RT 만료) | 401 | `AUTH401_5` |
| 쿠키의 RT와 저장된 RT가 불일치 | 401 | `AUTH401_6` |

RTR이므로 **한 번 쓴 RT를 다시 보내면 `AUTH401_6`과 함께 `rt:{userId}`가 삭제되어 그 회원의 세션이 모두 끊긴다.**

### 로그아웃

| | |
| --- | --- |
| Method | `POST` |
| Path | `/api/v1/auth/logout` |
| 인증 | 필요 |

RT를 Redis에서 지우고, 요청에 쓰인 AT를 남은 만료 시간만큼 차단 목록에 올린다.

**Request** — 본문 없음. `Authorization: Bearer {AT}`

**Response** (`200`, `AUTH200_2`) — `result`는 `null`. `Set-Cookie`로 `refreshToken` 쿠키를 `Max-Age=0`으로 지운다.

AT가 만료된 상태로는 호출할 수 없다. 재발급 후 호출한다. 같은 AT로 두 번 호출하면 두 번째는 `AUTH401_4`다.

### 회원 탈퇴

| | |
| --- | --- |
| Method | `DELETE` |
| Path | `/api/v1/users/me` |
| 인증 | 필요 |

본인 계정을 논리 삭제하고 개인정보를 파기한다. `users.withdrawn_at`을 채우고 `email`·`provider_id`를 `NULL`로 만든다 (ERD 1.2.3). 토큰 처리는 로그아웃과 같다.

**Request** — 본문 없음. `Authorization: Bearer {AT}`

**Response** (`200`, `USER200_1`) — `result`는 `null`. `refreshToken` 쿠키를 지운다.

**에러**

| 상황 | HTTP | code |
| --- | --- | --- |
| 회원을 찾을 수 없음 | 404 | `USER404_1` |

`provider_id`가 `NULL`이 되므로 같은 카카오 계정으로 다시 로그인하면 **새 회원으로 가입**된다. 카카오 연결 끊기(unlink)는 이번 범위에 없다.

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