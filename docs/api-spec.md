# API 명세

> 최종 수정: 2026-11-05
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
현재 두 핸들러는 로그만 남긴다 — **JWT 발급과 리다이렉트는 미구현**이라 클라이언트에 전달되는 응답 본문이 없다.

필수 동의 항목: `profile_nickname`, `account_email`. 누락 시 `AUTH401_1`.

<!-- TODO: JWT 발급 방식이 결정되면 토큰 전달 방법(쿠키/리다이렉트 파라미터)과 재발급·로그아웃 엔드포인트를 추가한다. -->

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