# API 명세

> 최종 수정: 2026-11-14
> 구현된 REST 엔드포인트는 인증 2개(토큰 재발급 · 로그아웃), 사용자 6개(내 정보 · 약관 동의 · 프로필 등록/수정 · 설정 조회 · 회원 탈퇴), 수업 기록 1개(저장), 교육비 2개(입금 현황 조회 · 입금 확인)다. 카카오 로그인은 Spring Security OAuth2 Client가 제공하는 경로를 그대로 쓴다.
> 작성 규칙은 `.claude/rules/api-convention.md`, 코드 체계는 `.claude/rules/error-code-convention.md`를 따른다.

## 공통

- Base URL(로컬): `http://localhost:8080`
- 모든 엔드포인트 prefix: `/api/v1`
- 요청/응답 Content-Type: `application/json;charset=UTF-8`
- `/oauth2/**`, `/login/**`, `/api/v1/auth/reissue`, Swagger 경로(`/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`)를 제외한 모든 경로는 인증이 필요하다 (`SecurityConfig.allowUris`).
- Swagger UI: `http://localhost:8080/swagger-ui.html`, OpenAPI 문서(JSON): `http://localhost:8080/v3/api-docs`. Swagger는 `@Tag`·`@Operation` 수준의 요약과 시험 호출용이고, **에러 코드별 응답과 쿠키·헤더 규칙은 이 문서가 기준이다.**

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
| `USER200_2` | 200 | 사용자 정보가 조회되었습니다. |
| `USER200_3` | 200 | 설정 정보가 조회되었습니다. |
| `USER200_4` | 200 | 프로필이 수정되었습니다. |
| `USER201_1` | 201 | 약관 동의가 저장되었습니다. |
| `USER201_2` | 201 | 프로필이 등록되었습니다. |

**수업 기록 (`RecordSuccessCode`)**

| code | HTTP | message |
| --- | --- | --- |
| `RECORD201_1` | 201 | 수업 기록을 저장했습니다. |

**정산 리포트 (`ReportSuccessCode`)**

| code | HTTP | message |
| --- | --- | --- |
| `REPORT200_1` | 200 | 정산 리포트를 조회했습니다. |

**교육비 (`InvoiceSuccessCode`)**

| code | HTTP | message |
| --- | --- | --- |
| `INVOICE200_1` | 200 | 입금 현황이 성공적으로 조회되었습니다. |
| `INVOICE201_1` | 201 | 입금이 성공적으로 확인되었습니다. |

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
| `USER400_1` | 400 | 필수 약관에 모두 동의해야 합니다. |
| `USER400_2` | 400 | 기타 과목명을 입력해 주세요. |
| `USER404_1` | 404 | 해당 회원을 찾을 수 없습니다. |
| `USER404_2` | 404 | 프로필이 등록되지 않았습니다. |
| `USER409_1` | 409 | 이미 등록된 프로필입니다. |

**수업 기록 (`RecordErrorCode`)**

| code | HTTP | message |
| --- | --- | --- |
| `RECORD400_1` | 400 | 수업 일정에 없는 날짜입니다. |
| `RECORD400_2` | 400 | 아직 하지 않은 수업은 기록할 수 없습니다. |
| `RECORD400_3` | 400 | 끝 진도는 시작 진도보다 앞설 수 없습니다. |
| `RECORD404_1` | 404 | 과외를 찾을 수 없습니다. |
| `RECORD404_2` | 404 | 수업 일정을 찾을 수 없습니다. |
| `RECORD404_3` | 404 | 교재를 찾을 수 없습니다. |
| `RECORD409_1` | 409 | 이미 기록한 수업입니다. |
| `RECORD409_2` | 409 | 진행 중인 수업 사이클이 없습니다. |

**정산 리포트 (`ReportErrorCode`)**

| code | HTTP | message |
| --- | --- | --- |
| `REPORT404_1` | 404 | 정산 리포트를 찾을 수 없습니다. |

**교육비 (`InvoiceErrorCode`)**

| code | HTTP | message |
| --- | --- | --- |
| `INVOICE404_1` | 404 | 교육비 안내를 찾을 수 없습니다. |
| `INVOICE409_1` | 409 | 취소된 교육비 안내입니다. |
| `INVOICE409_2` | 409 | 이미 받은 금액보다 적게 바꿀 수 없습니다. |
| `INVOICE409_3` | 409 | 이미 입금 완료된 교육비입니다. |
| `INVOICE409_4` | 409 | 이미 보낸 정산 리포트의 교육비는 바꿀 수 없습니다. |
| `INVOICE409_5` | 409 | 입금 확인을 요청할 수 없는 상태입니다. |

`INVOICE404_1`은 청구가 없을 때와 **다른 선생님의 과외 청구**일 때 모두 쓴다 (남의 청구가 있는지 드러내지 않는다). `INVOICE409_2` · `409_4` · `409_5`는 교육비 수정 · 학부모 [입금했어요] API에서 쓴다.

<!-- TODO: 도메인이 추가되면 에러 코드 표를 도메인별로 덧붙인다. -->

### 계산 값 이름

교육비 금액은 응답마다 같은 이름 · 같은 뜻으로 쓴다. 수입 탭, 입금 확인, 정산 리포트 교육비 블록 등 새 응답에서 아래 값을 내려줄 때는 이 이름을 그대로 쓴다. 단위는 모두 원(정수)이다.

| 이름 | 뜻 |
| --- | --- |
| `amount` | 최종 청구 금액. 자동 계산 금액에 교육비 수정을 반영한 값 (`invoices.amount`) |
| `paidAmount` | 그 청구에 확인된 입금 중 되돌리지 않은(`canceled_at` 없음) 것의 합 |
| `balance` | 남은 금액 = `amount` − `paidAmount`. 0보다 작아지지 않는다 |
| `previousUnpaidAmount` | 같은 과외에서 이 청구보다 먼저 만든, 취소되지 않은 청구들의 `balance` 합 (미납 잔액, 6.1.2). 이번 `amount`에 더하지 않고 별도 줄로 보여준다 |
| `unitPrice` | 회당 단가 = 수업료 ÷ 정산 회차, 원 미만 버림 (청구 스냅샷) |
| `recognizedCount` | 청구에 반영한 인정 회차. 후불 · 수업 종료 청구에만 있고 선불은 `null` (청구 스냅샷) |
| `settlementSessionCount` | 정산 회차 = 사이클 1번의 수업 횟수 (청구 스냅샷). 수입 탭 이름 '첫 · 다음 · 이번 N회차'의 N |

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

공개 경로(`/oauth2/**`, `/login/**`, `/api/v1/auth/reissue`, Swagger 경로)는 만료된 토큰을 함께 보내도 그대로 통과한다.

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

---

## 사용자

모두 인증이 필요하고 본인(`/me`) 자원만 다룬다. 회원은 가입 직후 **약관 동의 → 프로필 등록** 순으로 온보딩을 마쳐야 하며, 진행 상태는 `GET /users/me`의 `status`로 알 수 있다.

### 내 정보 조회

| | |
| --- | --- |
| Method | `GET` |
| Path | `/api/v1/users/me` |
| 인증 | 필요 |

**Response** (`200`, `USER200_2`)

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `role` | string | `TEACHER` (현재 고정) |
| `name` | string \| null | 프로필 이름. 프로필이 없으면 `null` |
| `status` | string | `TERMS_REQUIRED` / `PROFILE_REQUIRED` / `ACTIVE` |

`status`는 저장하지 않고 계산한다. 현재 시행 중인 **필수** 약관 전부에 동의 행이 없으면 `TERMS_REQUIRED`, 동의는 됐지만 프로필이 없으면 `PROFILE_REQUIRED`, 둘 다 있으면 `ACTIVE`. 새 버전 약관이 시행되면 그 버전에 다시 동의할 때까지 `TERMS_REQUIRED`로 돌아간다.

**에러**

| 상황 | HTTP | code |
| --- | --- | --- |
| 회원을 찾을 수 없음 | 404 | `USER404_1` |

### 약관 동의

| | |
| --- | --- |
| Method | `POST` |
| Path | `/api/v1/users/me/terms-agreements` |
| 인증 | 필요 |

코드별로 현재 시행 중인 최신 버전 약관에 동의한 것으로 기록한다. 약관 행(`terms`)은 코드에서 만들지 않고 `data.sql` 시드로 넣는다 (`SERVICE` / `PRIVACY` / `CONTACT`, 모두 필수).

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `termsCodes` | string[] | O | 1개 이상, `SERVICE` / `PRIVACY` / `CONTACT` | 동의한 약관 코드. 중복은 무시한다 |

**Response** (`201`, `USER201_1`)

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `agreements[].code` | string | 약관 코드 |
| `agreements[].agreedAt` | datetime | 동의 시각 |

멱등이다. 이미 같은 버전에 동의한 코드는 행을 새로 만들지 않고 기존 `agreedAt`을 그대로 돌려준다.

**에러**

| 상황 | HTTP | code |
| --- | --- | --- |
| `termsCodes`가 비어 있음, 알 수 없는 코드 | 400 | `COMMON400` |
| 현재 시행 중인 필수 약관이 하나라도 빠짐 (저장 전 검사라 부분 저장 없음) | 400 | `USER400_1` |
| 회원을 찾을 수 없음 | 404 | `USER404_1` |

### 프로필 등록

| | |
| --- | --- |
| Method | `POST` |
| Path | `/api/v1/users/me/profile` |
| 인증 | 필요 |

**Request Body** (프로필 수정과 같은 형식)

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `name` | string | O | 2~10자 | 이름 |
| `subjects` | string[] | O | 1개 이상, `ENGLISH` / `MATH` / `KOREAN` / `SCIENCE` / `SOCIAL` / `OTHER` | 과목. 중복은 무시한다 |
| `subjectOther` | string | `OTHER` 포함 시 O | 20자 이내 | 기타 과목명. `OTHER`가 없으면 값이 있어도 버린다 |

**Response** (`201`, `USER201_2`)

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `createdAt` | datetime | 등록 시각 |

**에러**

| 상황 | HTTP | code |
| --- | --- | --- |
| 이름 길이, 빈 과목 목록, 알 수 없는 과목, 기타 과목명 20자 초과 | 400 | `COMMON400` |
| `OTHER`를 골랐는데 `subjectOther`가 비어 있음 | 400 | `USER400_2` |
| 회원을 찾을 수 없음 | 404 | `USER404_1` |
| 이미 프로필이 있음 | 409 | `USER409_1` |

약관 동의 여부는 검사하지 않는다 (온보딩 순서는 프론트가 `status`로 안내한다).

### 프로필 수정

| | |
| --- | --- |
| Method | `PUT` |
| Path | `/api/v1/users/me/profile` |
| 인증 | 필요 |

이름 · 과목 · 기타 과목명을 **전체 교체**한다. 부분 수정은 없다.

**Request Body** — 프로필 등록과 같다.

**Response** (`200`, `USER200_4`)

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `updatedAt` | datetime | 수정 시각 |

**에러**

| 상황 | HTTP | code |
| --- | --- | --- |
| 요청 검증 실패 | 400 | `COMMON400` |
| `OTHER`를 골랐는데 `subjectOther`가 비어 있음 | 400 | `USER400_2` |
| 프로필이 없음 | 404 | `USER404_2` |

### 설정 조회

| | |
| --- | --- |
| Method | `GET` |
| Path | `/api/v1/users/me/settings` |
| 인증 | 필요 |

설정 화면 한 번에 필요한 정보를 묶어 돌려준다. **`profile` · `account`만 DB 값**이고 나머지 묶음은 해당 도메인(소개서 · 결제 · 알림)이 생기기 전까지 고정 기본값이다.

**Response** (`200`, `USER200_3`)

```json
{
  "profile": { "name": "한서윤", "subjects": [ { "code": "ENGLISH", "name": "영어" }, { "code": "OTHER", "name": "논술" } ] },
  "account": { "provider": "KAKAO" },
  "intro": { "status": "NONE", "unlockedAt": null },
  "curriculumPass": { "availableCount": 0, "nearestExpiresAt": null, "hasPendingRequest": false },
  "subscription": { "status": "NONE", "nextBillingOn": null, "hasPendingRequest": false },
  "notification": { "sentCount": 0, "pushEnabled": false }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `profile.name` | string | 이름 |
| `profile.subjects[].code` | string | 과목 코드 |
| `profile.subjects[].name` | string | 표시명. `OTHER`는 enum 한글명(`기타`) 대신 `subjectOther` 값 |
| `account.provider` | string | 로그인 수단 (`KAKAO`) |
| `intro` · `curriculumPass` · `subscription` · `notification` | object | 고정 기본값 (위 예시 그대로) |

**에러**

| 상황 | HTTP | code |
| --- | --- | --- |
| 프로필이 없음 | 404 | `USER404_2` |

### 회원 탈퇴

| | |
| --- | --- |
| Method | `DELETE` |
| Path | `/api/v1/users/me` |
| 인증 | 필요 |

본인 계정을 논리 삭제하고 개인정보를 파기한다. `users.withdrawn_at`을 채우고 `email`·`provider_id`를 `NULL`로 만든다 (ERD 1.2.3). 토큰 처리는 로그아웃(인증 절)과 같다.

**Request** — 본문 없음. `Authorization: Bearer {AT}`

**Response** (`200`, `USER200_1`) — `result`는 `null`. `refreshToken` 쿠키를 지운다.

**에러**

| 상황 | HTTP | code |
| --- | --- | --- |
| 회원을 찾을 수 없음 | 404 | `USER404_1` |

`provider_id`가 `NULL`이 되므로 같은 카카오 계정으로 다시 로그인하면 **새 회원으로 가입**된다. 카카오 연결 끊기(unlink)는 이번 범위에 없다.

---

## 수업 기록

### 수업 기록 저장

| | |
| --- | --- |
| Method | `POST` |
| Path | `/api/v1/tutorings/{tutoringId}/records` |
| 인증 | 필요 (본인 과외만) |

반복 일정의 수업 한 회차를 기록한다. 회차(`sessions`) · 기록(`session_records`) · 진도(`session_progress`) 행을 함께 만든다.
이번 기록으로 사이클의 인정 회차 수가 정산 회차에 닿으면 같은 트랜잭션에서 정산한다: 사이클 SETTLED · 다음 사이클 열기 → 정산 리포트 생성 → 교육비 청구 생성.
정산 중 하나라도 실패하면 기록 저장까지 전부 롤백된다. 보강 · 추가 회차 기록은 아직 없다.

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `scheduleId` | number | O | 이 과외의 반복 일정 | 기록할 회차의 일정 |
| `originalDate` | string | O | `yyyy-MM-dd`, 일정의 요일 · 적용 기간 안, 오늘 이전(오늘 포함) | 반복 일정상 수업 날짜 |
| `progress` | array | O | 1~5개, `textbookId` 중복 불가 | 교재별 진도. [안 함] 교재는 넣지 않는다 |
| `progress[].textbookId` | number | O | 이 과외의 교재 | 교재 ID |
| `progress[].startPosition` | number | O | 0 이상 | 이번 수업 시작 진도 |
| `progress[].endPosition` | number | O | `startPosition` 이상 | 이번 수업 끝 진도 |
| `understanding` | string | O | `HIGH` / `MEDIUM` / `LOW` | 이해도 |
| `previousHomeworkResult` | string | X | `DONE` / `PARTIAL` / `NOT_DONE` | 지난 숙제. 첫 회차 · 지난 숙제 없으면 null |
| `nextHomework` | string | X | 100자 이내 | 다음 숙제 |
| `observationMemo` | string | X | 100자 이내 | 관찰 한 줄 |

```json
{
  "scheduleId": 31,
  "originalDate": "2026-09-30",
  "progress": [
    { "textbookId": 11, "startPosition": 7, "endPosition": 7 },
    { "textbookId": 12, "startPosition": 18, "endPosition": 18 }
  ],
  "understanding": "HIGH",
  "previousHomeworkResult": "DONE",
  "nextHomework": "7강 예습",
  "observationMemo": null
}
```

**Response** (`201`, `RECORD201_1`)

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `sessionId` | number | 이번 기록으로 만든 회차 ID |
| `noInCycle` | number | 사이클 안에서 몇 번째 인정 회차인지 (1부터). 지난 수업을 늦게 기록하면 마지막 번호가 아닐 수 있다 |
| `cycle.cycleNo` | number | 기록이 들어간 사이클 번호. 정산했으면 방금 닫은 사이클 |
| `cycle.recognizedCount` | number | 사이클의 인정 회차 수 (이번 기록 포함) |
| `cycle.settlementSessionCount` | number | 사이클에 적용되는 정책의 정산 회차 |
| `settlement.settled` | boolean | 이번 기록으로 정산했으면 `true` |
| `settlement.cycleId` | number \| null | 정산한(방금 닫은) 사이클 ID. 정산하지 않았으면 `null` |
| `settlement.reportId` | number \| null | 만든 정산 리포트 ID (`GET /api/v1/reports/{reportId}`). 정산하지 않았으면 `null` |
| `settlement.invoiceId` | number \| null | 만든 교육비 청구 ID. 정산하지 않았으면 `null` |

```json
{
  "sessionId": 1198,
  "noInCycle": 7,
  "cycle": { "cycleNo": 1, "recognizedCount": 7, "settlementSessionCount": 8 },
  "settlement": { "settled": false, "cycleId": null, "reportId": null, "invoiceId": null }
}
```

정산 회차를 채운 기록이면:

```json
{
  "sessionId": 1199,
  "noInCycle": 8,
  "cycle": { "cycleNo": 1, "recognizedCount": 8, "settlementSessionCount": 8 },
  "settlement": { "settled": true, "cycleId": 88, "reportId": 21, "invoiceId": 301 }
}
```

**에러**

| 상황 | HTTP | code |
| --- | --- | --- |
| 필수값 누락, 교재 개수 · 중복, 100자 초과, 음수 진도 | 400 | `COMMON400` (`result`에 필드별 메시지) |
| 날짜 형식 오류, 알 수 없는 enum 값 | 400 | `COMMON400` (`result`는 `null`) |
| 일정의 요일 · 적용 기간과 맞지 않는 날짜 | 400 | `RECORD400_1` |
| 오늘보다 뒤의 날짜 | 400 | `RECORD400_2` |
| 시작 진도가 끝 진도보다 큼 | 400 | `RECORD400_3` |
| 과외가 없거나 삭제됐거나 다른 선생님의 과외 | 404 | `RECORD404_1` |
| 일정이 없거나 이 과외의 일정이 아님 | 404 | `RECORD404_2` |
| 교재가 없거나 이 과외의 교재가 아님 | 404 | `RECORD404_3` |
| 같은 일정 · 같은 날짜의 회차를 이미 기록함 | 409 | `RECORD409_1` |
| 과외에 진행 중 사이클이 없음 | 409 | `RECORD409_2` |
| 정산할 사이클에 적용할 정책이 없음 (데이터 이상) | 500 | `SETTLEMENT500_1` |

동시 요청은 고려하지 않는다. 같은 회차를 동시에 저장하면 늦은 요청은 유니크 제약에 걸려 `COMMON500`이다.

---

## 정산 리포트

### 정산 리포트 조회

| | |
| --- | --- |
| Method | `GET` |
| Path | `/api/v1/reports/{reportId}` |
| 인증 | 필요 (본인 과외의 리포트만) |

정산 리포트의 네 영역(요약 · 진도 · 선생님 코멘트 · 교육비 안내) 중 요약과 진도를 조회한다.
리포트는 수업 기록 저장이 정산 회차를 채울 때 만들어진다 (기록 저장 응답의 `settlement.reportId`).
통계와 진도 표는 생성 시점에 고정된 값이고, 기간(`period`)만 조회 때 사이클 인정 회차로 계산한다.

**Path Variable**

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `reportId` | number | 정산 리포트 ID |

**Response** (`200`, `REPORT200_1`)

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `reportId` | number | 정산 리포트 ID |
| `status` | string | 리포트 상태. 발송 기능 전에는 항상 `GENERATED` |
| `studentName` | string | 학생 이름 |
| `period.startDate` / `period.endDate` | string | 사이클 인정 회차의 첫 / 마지막 수업일 (`yyyy-MM-dd`) |
| `stats.completedCount` | number | 진행한 인정 회차 수 |
| `stats.targetCount` | number | 정산 회차 |
| `stats.canceledCount` | number | 사이클 기간 안의 휴강 수 |
| `stats.homeworkRate` | number \| null | 숙제 수행률 %. (DONE + PARTIAL × 0.5) ÷ 지난 숙제 결과가 있는 기록 수, 반올림. 결과가 없으면 `null` |
| `stats.prevHomeworkRate` | number \| null | 직전 사이클 리포트의 수행률. 없으면 `null` |
| `stats.understanding.high` / `medium` / `low` | number | 이해도 상 · 중 · 하 횟수 |
| `progress[].textbookName` | string | 교재 이름 (생성 시점) |
| `progress[].startLabel` / `endLabel` | string | 사이클 첫 회차의 시작 진도 / 마지막 회차의 끝 진도. 단위별 형식: `Unit 12`, `Chapter 3`, `3단원`, `45p`, `3회`, 직접 입력 단위는 숫자 + 단위(`9강`) |
| `progress[].textbookStatus` | string | 기간 중 추가 · 사용 종료 표기. 지금은 항상 `ACTIVE` |

`progress`는 교재 ID 순이고, 사이클 동안 진도 기록이 없는 교재는 빠진다.

```json
{
  "reportId": 21,
  "status": "GENERATED",
  "studentName": "윤서아",
  "period": { "startDate": "2026-09-14", "endDate": "2026-09-30" },
  "stats": {
    "completedCount": 8,
    "targetCount": 8,
    "canceledCount": 0,
    "homeworkRate": 88,
    "prevHomeworkRate": 75,
    "understanding": { "high": 5, "medium": 3, "low": 0 }
  },
  "progress": [
    { "textbookName": "수능특강 영어", "startLabel": "3강", "endLabel": "9강", "textbookStatus": "ACTIVE" },
    { "textbookName": "Grammar Zone 기본", "startLabel": "Unit 12", "endLabel": "Unit 20", "textbookStatus": "ACTIVE" }
  ]
}
```

**에러**

| 상황 | HTTP | code |
| --- | --- | --- |
| `reportId`가 숫자가 아님 | 400 | `COMMON400` |
| 리포트가 없거나 다른 선생님 과외의 리포트 | 404 | `REPORT404_1` |

---

## 교육비

선생님 수입 탭 API다. 모두 인증이 필요하고 **내 과외**(`tutorings.teacher_id` = 로그인 사용자, 삭제되지 않음)의 청구만 다룬다. 금액 이름의 뜻은 공통 절 '계산 값 이름'을 따른다.

### 입금 현황 조회

| | |
| --- | --- |
| Method | `GET` |
| Path | `/api/v1/invoices` |
| 인증 | 필요 (선생님) |

수입 탭 화면 전체를 한 번에 돌려준다. 페이징은 없다.

**Request Query**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `month` | string | X | `YYYY-MM` (월 01~12) | 조회할 달. 없으면 한국 시간 이번 달 |

**대상 청구** — 취소(CANCELED)를 뺀 내 과외 청구 중 아래 둘 중 하나. 종료된 과외도 포함한다.

1. 안내일(`notified_at`, 없으면 `created_at`)이 그 달 `[1일 00:00, 다음 달 1일 00:00)` 안인 청구
2. 안내일이 그 달 이전이고 `balance`가 남은 청구 (PENDING · PARTIAL · CONFIRM_REQUESTED)

**정렬** — 확인 대기(CONFIRM_REQUESTED) → 입금 대기(PENDING) → 일부 입금(PARTIAL) → 입금 완료(PAID). 같은 상태는 안내일 최신순, 같으면 `invoiceId` 큰 순.

**Response** (`200`, `INVOICE200_1`)

```json
{
  "confirmRequestedCount": 1,
  "unpaidCount": 2,
  "summary": { "totalAmount": 720000, "paidAmount": 120000, "unpaidAmount": 600000 },
  "items": [
    {
      "invoiceId": 12,
      "tutoringId": 3,
      "studentName": "서아",
      "invoiceName": "이번 8회차",
      "amount": 360000,
      "paidAmount": 120000,
      "balance": 240000,
      "previousUnpaidAmount": 0,
      "status": "PARTIAL",
      "statusLine": { "type": "PARTIAL_PAID", "at": "2026-11-15T20:00:00" },
      "reportId": 5,
      "notifiedAt": "2026-11-10T19:02:11",
      "createdAt": "2026-11-10T18:40:03"
    }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `confirmRequestedCount` | number | items 중 CONFIRM_REQUESTED 건수 |
| `unpaidCount` | number | items 중 PENDING · PARTIAL · CONFIRM_REQUESTED 건수 |
| `summary.totalAmount` | number | items의 `amount` 합 |
| `summary.paidAmount` | number | items의 `paidAmount` 합 |
| `summary.unpaidAmount` | number | items의 `balance` 합 |
| `items[].invoiceId` | number | 청구 ID |
| `items[].tutoringId` | number | 과외 ID |
| `items[].studentName` | string \| null | 학생 이름 (과외 삭제 시 파기돼 `null`) |
| `items[].invoiceName` | string | FIRST `첫 N회차` / NEXT `다음 N회차` / CURRENT `이번 N회차` / FINAL `마지막 정산`. N = `settlementSessionCount` |
| `items[].amount` · `paidAmount` · `balance` · `previousUnpaidAmount` | number | 계산 값 이름 참고 |
| `items[].status` | string | `PENDING` / `CONFIRM_REQUESTED` / `PARTIAL` / `PAID` |
| `items[].statusLine.type` | string | 아래 표 |
| `items[].statusLine.at` | datetime \| null | 아래 표 |
| `items[].reportId` | number \| null | 함께 보낸 정산 리포트 ID. 선불 첫 청구 등은 `null` |
| `items[].notifiedAt` | datetime \| null | 교육비 안내 첫 발송 시각. 아직 안 보냈으면 `null` |
| `items[].createdAt` | datetime | 청구 생성 시각 |

건수와 `summary`는 따로 집계하지 않고 **items에서 더한다**. 화면의 합계와 목록이 항상 맞는다.

**`statusLine`** — 위에서부터 먼저 맞는 하나. 저장하지 않고 계산한다.

| 순서 | `type` | 조건 | `at` |
| --- | --- | --- | --- |
| 1 | `PARENT_CONFIRMED` | CONFIRM_REQUESTED (학부모가 [입금했어요]) | 요청 시각 (`confirm_requested_at`) |
| 2 | `PARTIAL_PAID` | PARTIAL | 되돌리지 않은 입금 중 마지막 확인 시각 |
| 3 | `NONE` | PAID — 다시 보냈거나 안내 전이어도 더 알릴 것이 없다 | `null` |
| 4 | `RESENT` | `renotified_at` 있음 | 다시 보낸 시각 |
| 5 | `REMINDER_SCHEDULED` | 과외 공유 설정의 미입금 알림 켜짐 + `reminder_sent_at` 없음 + PENDING | `null` |
| 6 | `NOT_SENT` | `notified_at` 없음 | `null` |
| 7 | `NONE` | 그 외 | `null` |

**에러**

| 상황 | HTTP | code |
| --- | --- | --- |
| `month` 형식 오류 (`2026-13`, `2026-1` 등). `result`는 `{ "month": "월은 YYYY-MM 형식이어야 합니다." }` | 400 | `COMMON400` |

### 입금 확인

| | |
| --- | --- |
| Method | `POST` |
| Path | `/api/v1/invoices/{invoiceId}/payments` |
| 인증 | 필요 (선생님) |

선생님이 확인한 입금([전액 입금 완료] · 일부 입금, 6.3.1)을 기록한다. 한 트랜잭션에서 청구 행을 잠그고(SELECT … FOR UPDATE) 입금 행 추가와 상태 변경을 함께 한다. 합계가 `amount` 이상이면 PAID, 아니면 PARTIAL. 확인 대기(CONFIRM_REQUESTED)에서 입금해도 같은 규칙이다. 학부모에게 알림은 보내지 않는다.

**Request Path**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `invoiceId` | number | 청구 ID |

**Request Body** (본문을 생략해도 된다)

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `amount` | number | X | 1 이상 | 확인한 금액. 없으면 `balance` 전부, `balance`보다 크면 `balance`까지만 반영 |
| `confirmedAt` | datetime | X | `yyyy-MM-ddTHH:mm:ss` (한국 시간) | 입금 확인 시각 (수입 탭 월별 합계 기준). 없으면 요청 시각 |

```json
{ "amount": 120000, "confirmedAt": "2026-11-15T20:00:00" }
```

`Idempotency-Key` 헤더는 아직 받지 않는다. 같은 요청을 두 번 보내면 입금이 두 번 기록된다(잔액을 넘지는 않는다).

**Response** (`201`, `INVOICE201_1`)

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `paymentId` | number | 새 입금 행 ID |
| `appliedAmount` | number | 실제로 반영한 금액 |
| `item` | object | 반영 후의 교육비 한 줄. 입금 현황 조회의 `items[]`와 같은 형식 · 같은 변환 |

예: 360,000원 청구에 `{ "amount": 120000 }` → `appliedAmount` 120000, `item.status` PARTIAL, `item.statusLine.type` PARTIAL_PAID, `item.balance` 240000. 이어서 `{ "amount": 300000 }` → `appliedAmount` 240000, `item.status` PAID.

**에러**

| 상황 | HTTP | code |
| --- | --- | --- |
| `amount`가 0 이하, 깨진 JSON. 검증 실패면 `result`는 `{ "amount": "입금 금액은 1원 이상이어야 합니다." }` | 400 | `COMMON400` |
| 청구가 없음, 다른 선생님 과외의 청구, 삭제된 과외의 청구 | 404 | `INVOICE404_1` |
| 취소된 청구 | 409 | `INVOICE409_1` |
| 이미 입금 완료 (`balance` 0) | 409 | `INVOICE409_3` |

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