# API 명세

> 최종 수정: 2026-11-26 · 규칙: `.claude/rules/api-convention.md`, `.claude/rules/error-code-convention.md`

## 공통

- Base URL(로컬) `http://localhost:8080`, prefix `/api/v1`, `application/json;charset=UTF-8`
- 인증 불필요 경로: `/oauth2/**`, `/login/**`, `/api/v1/auth/reissue`, `/api/v1/links/**`(학부모 링크, 토큰이 열쇠), Swagger(`/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`). 그 외는 `Authorization: Bearer {AT}` 필요.
- Swagger는 요약 · 시험 호출용이고, 에러 코드 · 쿠키 · 헤더 규칙은 이 문서가 기준이다.

### 응답 형식

```json
{ "isSuccess": true, "code": "COMMON200", "message": "성공적으로 요청을 처리했습니다.", "result": { } }
```

`result`는 성공 시 데이터, 검증 실패 시 `{필드명: 메시지}`, 그 외 실패 시 `null`.

### 성공 코드

| code | HTTP | message |
| --- | --- | --- |
| `AUTH200_1` | 200 | 토큰을 재발급했습니다. |
| `AUTH200_2` | 200 | 로그아웃되었습니다. |
| `USER200_1` | 200 | 회원 탈퇴가 완료되었습니다. |
| `USER200_2` | 200 | 사용자 정보가 조회되었습니다. |
| `USER200_3` | 200 | 설정 정보가 조회되었습니다. |
| `USER200_4` | 200 | 프로필이 수정되었습니다. |
| `USER201_1` | 201 | 약관 동의가 저장되었습니다. |
| `USER201_2` | 201 | 프로필이 등록되었습니다. |
| `RECORD200_1` | 200 | 기록 화면 정보를 조회했습니다. |
| `RECORD201_1` | 201 | 수업 기록을 저장했습니다. |
| `SCHEDULE200_1` | 200 | 한 달 달력이 성공적으로 조회되었습니다. |
| `SCHEDULE200_2` | 200 | 그날 일정이 성공적으로 조회되었습니다. |
| `REPORT200_1` | 200 | 정산 리포트를 조회했습니다. |
| `REPORT200_2` | 200 | 정산 리포트 코멘트를 조회했습니다. |
| `REPORT200_3` | 200 | 정산 리포트 코멘트를 저장하고 확인했습니다. |
| `REPORT200_4` | 200 | 정산 리포트를 보냈습니다. |
| `REPORT200_5` | 200 | 정산 리포트 목록을 조회했습니다. |
| `REPORT200_6` | 200 | 정산 리포트를 열었습니다. |
| `INVOICE200_1` | 200 | 입금 현황이 성공적으로 조회되었습니다. |
| `INVOICE200_2` | 200 | 교육비 안내가 성공적으로 조회되었습니다. |
| `INVOICE200_3` | 200 | 교육비가 성공적으로 수정되었습니다. |
| `INVOICE200_4` | 200 | 입금 안내를 다시 보냈습니다. |
| `SHARE201_1` | 201 | 공유 정보가 생성되었습니다. |
| `INVOICE201_1` | 201 | 입금이 성공적으로 확인되었습니다. |

공통 `COMMON200` · `COMMON201`은 도메인에 속하지 않는 응답에만 쓴다.

### 에러 코드

공통: `COMMON400` 잘못된 요청 · `401` 인증 안 됨 · `403` 접근 금지 · `404` 리소스 없음 · `405` 메서드 미지원 · `406` 표현 형식 없음 · `409` 다른 요청이 먼저 처리됨 · `413` 본문 크기 초과 · `415` Content-Type 미지원 · `500` 서버 문제 · `503` 일시적 처리 불가.

| code | HTTP | message |
| --- | --- | --- |
| `AUTH400_1` | 400 | 지원하지 않는 소셜 로그인입니다. |
| `AUTH401_1` | 401 | 필수 동의 항목에 동의해야 로그인할 수 있습니다. |
| `AUTH401_2` | 401 | 소셜 로그인에 실패했습니다. |
| `AUTH401_3` | 401 | 유효하지 않은 토큰입니다. |
| `AUTH401_4` | 401 | 로그아웃된 토큰입니다. 다시 로그인해 주세요. |
| `AUTH401_5` | 401 | 다시 로그인해 주세요. (저장된 RT 없음) |
| `AUTH401_6` | 401 | 다시 로그인해 주세요. (RT 불일치 — 탈취 의심) |
| `USER400_1` | 400 | 필수 약관에 모두 동의해야 합니다. |
| `USER400_2` | 400 | 기타 과목명을 입력해 주세요. |
| `USER404_1` | 404 | 해당 회원을 찾을 수 없습니다. |
| `USER404_2` | 404 | 프로필이 등록되지 않았습니다. |
| `USER409_1` | 409 | 이미 등록된 프로필입니다. |
| `RECORD400_1` | 400 | 수업 일정에 없는 날짜입니다. |
| `RECORD400_2` | 400 | 아직 하지 않은 수업은 기록할 수 없습니다. |
| `RECORD400_3` | 400 | 끝 진도는 시작 진도보다 앞설 수 없습니다. |
| `RECORD404_1` | 404 | 과외를 찾을 수 없습니다. |
| `RECORD404_2` | 404 | 수업 일정을 찾을 수 없습니다. |
| `RECORD404_3` | 404 | 교재를 찾을 수 없습니다. |
| `RECORD409_1` | 409 | 이미 기록한 수업입니다. |
| `RECORD409_2` | 409 | 진행 중인 수업 사이클이 없습니다. |
| `SCHEDULE404_1` | 404 | 확인할 수 없는 과외입니다. |
| `SETTLEMENT500_1` | 500 | 정산에 적용할 과외 정책을 찾을 수 없습니다. |
| `REPORT404_1` | 404 | 정산 리포트를 찾을 수 없습니다. |
| `REPORT409_1` | 409 | 이미 보낸 정산 리포트는 고칠 수 없습니다. |
| `REPORT409_2` | 409 | 코멘트를 확인한 뒤에 보낼 수 있습니다. |
| `PARTICIPANT404_1` | 404 | 링크가 만료되었거나 올바르지 않습니다. |
| `SHARE400_1` | 400 | 아직 지원하지 않는 공유 종류입니다. |
| `SHARE400_2` | 400 | 공유할 대상(sourceId)을 함께 보내 주세요. |
| `SHARE404_1` | 404 | 받는 사람을 찾을 수 없습니다. |
| `SHARE409_1` | 409 | 수신을 거부한 받는 사람입니다. |
| `INVOICE404_1` | 404 | 교육비 안내를 찾을 수 없습니다. |
| `INVOICE409_1` | 409 | 취소된 교육비 안내입니다. |
| `INVOICE409_2` | 409 | 이미 받은 금액보다 적게 바꿀 수 없습니다. |
| `INVOICE409_3` | 409 | 이미 입금 완료된 교육비입니다. |
| `INVOICE409_4` | 409 | 이미 보낸 정산 리포트의 교육비는 바꿀 수 없습니다. |
| `INVOICE409_5` | 409 | 입금 확인을 요청할 수 없는 상태입니다. |
| `IDEMPOTENCY400_1` | 400 | 멱등 키(Idempotency-Key) 헤더가 없거나 형식이 올바르지 않습니다. |
| `IDEMPOTENCY409_1` | 409 | 같은 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요. |
| `IDEMPOTENCY422_1` | 422 | 이미 다른 요청에 사용한 멱등 키입니다. |

- `*404_1`(과외 · 리포트 · 청구)은 대상이 없을 때와 **다른 선생님의 것**일 때 모두 쓴다 (존재 여부를 드러내지 않음).
- `INVOICE409_5`는 아직 없는 학부모 [입금했어요] API용이다.

### 멱등 키 (`Idempotency-Key`)

두 번 실행되면 안 되는 POST는 요청 헤더 `Idempotency-Key`(UUID)를 받는다. API마다 **필수**인지 **선택**인지 적는다.

- 요청마다 새 UUID를 만들고, 재시도 · 버튼 두 번 누름에는 **같은 값**을 보낸다. 대소문자는 구분하지 않는다.
- 같은 회원이 24시간 안에 같은 키 · 같은 요청(메서드 + 경로 + 본문)을 다시 보내면 처리 없이 첫 성공 응답(상태 코드 · 본문)을 그대로 돌려준다. 응답 헤더 `Idempotent-Replayed: true`.
- 선택인 API는 헤더가 없으면 멱등 처리 없이 그대로 실행한다. 헤더가 있는데 UUID가 아니면 선택이어도 `IDEMPOTENCY400_1`.
- 4xx 응답은 저장하지 않아 고친 요청을 같은 키로 다시 보낼 수 있다.
- 5xx 응답이면 처리 중 표시를 **2분** 유지해, 그동안 같은 키는 `IDEMPOTENCY409_1`이다. 저장은 끝났는데 응답만 실패한 경우의 즉시 재실행을 막기 위해서다. 2분이 지나 같은 키로 보내면 다시 실행된다.

| 상황 | HTTP | code |
| --- | --- | --- |
| (필수) 헤더 없음 · UUID 형식 아님 | 400 | `IDEMPOTENCY400_1` |
| 같은 키의 첫 요청이 아직 처리 중 (또는 5xx 뒤 2분 안) | 409 | `IDEMPOTENCY409_1` |
| 헤더가 붙은 본문 64KB 초과 · chunked 전송 / `Content-Type`이 JSON 아님 | 413 / 415 | `COMMON413` / `COMMON415` |
| 같은 키 · 다른 경로나 본문 | 422 | `IDEMPOTENCY422_1` |

### 금액 이름

단위는 원(정수). 새 응답도 같은 이름 · 같은 뜻으로 쓴다.

| 이름 | 뜻 |
| --- | --- |
| `amount` | 최종 청구 금액 (자동 계산 + 교육비 수정 반영, `invoices.amount`) |
| `paidAmount` | 되돌리지 않은 입금 합 |
| `balance` | `amount − paidAmount`, 최소 0 |
| `previousUnpaidAmount` | 같은 과외의 이전 미취소 청구들의 `balance` 합. `amount`에 더하지 않는다 |
| `unitPrice` | 수업료 ÷ 정산 회차, 원 미만 버림 (스냅샷) |
| `recognizedCount` | 청구에 반영한 인정 회차. 선불은 `null` (스냅샷) |
| `settlementSessionCount` | 정산 회차 (스냅샷). '첫 · 다음 · 이번 N회차'의 N |
| `deductionAmount` | 정산 회차 × 회당 단가에서 자동 계산 금액(`calculated_amount`)이 모자란 만큼, 최소 0. 회차가 모자란 청구에서만 0보다 크다 |
| `billedCycleStartDate` | 청구 스냅샷 `cycleStartDate` = 정산한 사이클의 첫 인정 회차 날짜(`yyyy-MM-dd`). 정산 없이 만든 선불 첫 청구(FIRST)는 `null` |

---

## 인증

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/oauth2/authorization/kakao` | 카카오 로그인 시작 (Spring Security 제공) |
| `GET` | `/login/oauth2/code/kakao` | 카카오 콜백 |
| `POST` | `/api/v1/auth/reissue` | 토큰 재발급 (공개) |
| `POST` | `/api/v1/auth/logout` | 로그아웃 |

**카카오 콜백** — 필수 동의: `profile_nickname`, `account_email` (누락 시 `AUTH401_1`). 리다이렉트 대상은 `app.oauth2.success-redirect-uri` / `failure-redirect-uri`.
- 성공: `302 {성공 URI}#accessToken={AT}` + `Set-Cookie: refreshToken={RT}; Path=/; Max-Age=1209600; HttpOnly; SameSite=Lax` (개발 서버는 `Secure` 추가)
- 실패: `302 {실패 URI}#code={에러 코드}`

**인증 필터** — 서명 · 만료 · `typ=access` 확인 후 `jti`가 Redis 차단 목록(`bl:{jti}`)에 있는지 본다. AT 30분 / RT 14일.

| 상황 | HTTP | code |
| --- | --- | --- |
| 토큰 없음 | 401 | `COMMON401` |
| 만료 · 위조, RT를 AT 자리에 보냄 | 401 | `AUTH401_3` (재발급 후 재시도) |
| 로그아웃 · 탈퇴로 차단된 토큰 | 401 | `AUTH401_4` (바로 로그인 화면) |
| 권한 부족 | 403 | `COMMON403` |

**토큰 재발급** (`200`, `AUTH200_1`) — `Cookie: refreshToken={RT}`로 AT · RT를 함께 재발급(RTR). 응답 `{ accessToken }`, 새 RT는 위와 같은 쿠키로 내려간다.

| 상황 | HTTP | code |
| --- | --- | --- |
| 쿠키 누락, 서명 · 만료 실패, `typ≠refresh` | 401 | `AUTH401_3` |
| Redis에 `rt:{userId}` 없음 (로그아웃 · 탈퇴 · 만료) | 401 | `AUTH401_5` |
| 쿠키 RT ≠ 저장된 RT. 이때 `rt:{userId}`를 지워 모든 세션을 끊는다 | 401 | `AUTH401_6` |

**로그아웃** (`200`, `AUTH200_2`, `result: null`) — RT 삭제 + 요청 AT를 남은 만료 시간만큼 차단 + `refreshToken` 쿠키 삭제. 같은 AT로 다시 호출하면 `AUTH401_4`.

---

## 사용자

모두 본인(`/me`) 자원. 온보딩 순서: 약관 동의 → 프로필 등록 (`GET /users/me`의 `status`로 안내).

| Method | Path | 성공 | 설명 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/users/me` | `USER200_2` | 내 정보 |
| `POST` | `/api/v1/users/me/terms-agreements` | `USER201_1` | 약관 동의 |
| `POST` | `/api/v1/users/me/profile` | `USER201_2` | 프로필 등록 |
| `PUT` | `/api/v1/users/me/profile` | `USER200_4` | 프로필 수정 (전체 교체) |
| `GET` | `/api/v1/users/me/settings` | `USER200_3` | 설정 조회 |
| `DELETE` | `/api/v1/users/me` | `USER200_1` | 회원 탈퇴 |

**내 정보** — `{ role: "TEACHER", name: string|null, status }`. `status`는 계산값: 현재 필수 약관 중 미동의가 있으면 `TERMS_REQUIRED`, 프로필이 없으면 `PROFILE_REQUIRED`, 그 외 `ACTIVE`. 에러 `USER404_1`.

**약관 동의** — 요청 `{ termsCodes: ["SERVICE", "PRIVACY", "CONTACT"] }` (1개 이상, 중복 무시). 각 코드의 현재 최신 버전에 동의 기록. 응답 `{ agreements: [{ code, agreedAt }] }`. 멱등(이미 동의했으면 기존 `agreedAt`). 약관 행은 환경마다 직접 INSERT (`docs/architecture.md`).
- `COMMON400` 빈 목록 · 알 수 없는 코드 / `USER400_1` 필수 약관 누락(부분 저장 없음) / `USER404_1`

**프로필 등록 · 수정** — 요청 `{ name: 2~10자, subjects: [ENGLISH|MATH|KOREAN|SCIENCE|SOCIAL|OTHER] (1개 이상), subjectOther: 20자 이내, OTHER일 때 필수 }`. 응답 등록 `{ createdAt }`, 수정 `{ updatedAt }`. 약관 동의 여부는 검사하지 않는다.
- `COMMON400` 검증 실패 / `USER400_2` OTHER인데 `subjectOther` 없음 / 등록: `USER404_1`, `USER409_1` 이미 있음 / 수정: `USER404_2` 프로필 없음

**설정 조회** — `profile` · `account`만 DB 값, 나머지는 해당 도메인 전까지 고정 기본값. 에러 `USER404_2`.

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

`subjects[].name`은 `OTHER`면 `subjectOther` 값.

**회원 탈퇴** — 논리 삭제(`withdrawn_at`), `email` · `provider_id`를 `NULL`로 파기, 토큰 처리는 로그아웃과 같다. 같은 카카오 계정으로 다시 로그인하면 새 회원. 에러 `USER404_1`.

---

## 수업 기록

### 회차 기록 화면 조회 — `GET /api/v1/tutorings/{tutoringId}/record-form?scheduleId=&originalDate=` (`200`, `RECORD200_1`)

저장 전 반복 일정 회차의 기록 화면(T-10) 정보. 같은 (`scheduleId`, `originalDate`)로 아래 `POST records`에 저장한다. 저장된 회차(보강 · 시간 변경)를 `sessionId`로 여는 것과 기록 수정은 아직 없다.

| 파라미터 | 필수 | 제약 |
| --- | --- | --- |
| `scheduleId` | O | 이 과외의 반복 일정 |
| `originalDate` | O | `yyyy-MM-dd`, 일정 요일 · 적용 기간 · 과외 기간 안. 미래 날짜도 열 수 있다 (저장 시 `RECORD400_2`) |

```json
{
  "sessionId": null, "scheduleId": 31, "originalDate": "2026-09-30",
  "sessionDate": "2026-09-30", "startTime": "18:00", "endTime": "19:30",
  "sessionType": "REGULAR", "studentName": "윤서아", "noInCycle": 8,
  "cycle": { "cycleNo": 1, "recognizedCount": 7, "settlementSessionCount": 8 },
  "textbooks": [
    { "textbookId": 11, "name": "수능특강 영어", "progressUnit": "CUSTOM", "customUnitLabel": "강", "lastPosition": 6 },
    { "textbookId": 12, "name": "Grammar Zone 기본", "progressUnit": "UNIT", "customUnitLabel": null, "lastPosition": 17 }
  ],
  "previousHomework": "6강 예습",
  "record": null
}
```

- `noInCycle` · `cycle`: 일정 탭(`daily`) 카드와 같은 계산. 지난 미기록은 '지금 기록하면 될 번호'(진행 중 인정 수 + 1), `recognizedCount`는 이번 회차 제외.
- `textbooks[]`: 과외 교재 ID 순. `lastPosition` = 이 회차 날짜보다 **앞선** 기록의 마지막 끝 진도, 앞선 기록이 없으면 교재 시작 진도(`start_position`, 기본 0).
- `previousHomework`: 이 회차 날짜보다 앞선 직전 기록의 `nextHomework`. 직전 기록에 숙제가 없으면 더 이전으로 거슬러 가지 않고 `null`.
- '앞선' 기준이라 지난 수업을 늦게 기록해도 그 시점의 진도 · 숙제가 나온다.
- `sessionId` · `record`: 지금은 항상 `null` (`record`는 기록 수정에서 채운다).
- 이미 정산된 날짜인지는 검사하지 않는다 (저장 API와 같음. 일정 탭은 그런 회차를 만들지 않는다).

| 상황 | HTTP | code |
| --- | --- | --- |
| 파라미터 누락 (`result: null`) / 날짜 형식 · 타입 오류 (`result`에 파라미터 메시지) | 400 | `COMMON400` |
| 일정 요일 · 적용 기간 · 과외 기간 밖 | 400 | `RECORD400_1` |
| 과외(없음 · 삭제 · 남의 과외) / 일정 없음 | 404 | `RECORD404_1` / `_2` |
| 같은 일정 · 날짜로 저장된 회차 있음 (기록 · 휴강 · 시간 변경) / 진행 중 사이클 없음 | 409 | `RECORD409_1` / `_2` |

### 수업 기록 저장 — `POST /api/v1/tutorings/{tutoringId}/records` (`201`, `RECORD201_1`)

반복 일정의 한 회차를 기록한다(`sessions` · `session_records` · `session_progress`). 인정 회차가 정산 회차에 닿으면 **같은 트랜잭션에서 정산**한다: 사이클 SETTLED · 다음 사이클 열기 → 정산 리포트 → 교육비 청구. 실패하면 전부 롤백. 보강 · 추가 회차는 아직 없다.

| 요청 필드 | 필수 | 제약 |
| --- | --- | --- |
| `scheduleId` | O | 이 과외의 반복 일정 |
| `originalDate` | O | `yyyy-MM-dd`, 일정 요일 · 적용 기간 안, 오늘 이전(포함) |
| `progress[]` | O | 1~5개, `textbookId` 중복 불가. [안 함] 교재는 제외 |
| `progress[].textbookId` / `startPosition` / `endPosition` | O | 이 과외의 교재 / 0 이상 / `startPosition` 이상 |
| `understanding` | O | `HIGH` / `MEDIUM` / `LOW` |
| `previousHomeworkResult` | X | `DONE` / `PARTIAL` / `NOT_DONE`. 지난 숙제 없으면 `null` |
| `nextHomework`, `observationMemo` | X | 100자 이내 |

```json
{
  "scheduleId": 31, "originalDate": "2026-09-30",
  "progress": [ { "textbookId": 11, "startPosition": 7, "endPosition": 7 } ],
  "understanding": "HIGH", "previousHomeworkResult": "DONE", "nextHomework": "7강 예습", "observationMemo": null
}
```

응답:

```json
{
  "sessionId": 1199,
  "noInCycle": 8,
  "cycle": { "cycleNo": 1, "recognizedCount": 8, "settlementSessionCount": 8 },
  "settlement": { "settled": true, "cycleId": 88, "reportId": 21, "invoiceId": 301 }
}
```

- `noInCycle`: 사이클 안 인정 회차 순번(1부터). 늦게 기록한 지난 수업이면 마지막 번호가 아닐 수 있다.
- `cycle`: 기록이 들어간 사이클 현황. 정산했으면 방금 닫은 사이클 기준.
- `settlement`: 정산하지 않았으면 `settled: false`, ID는 모두 `null`.

| 상황 | HTTP | code |
| --- | --- | --- |
| 필드 검증 실패 (`result`에 필드별 메시지) / 날짜 형식 · enum 오류 (`result: null`) | 400 | `COMMON400` |
| 일정 요일 · 기간 밖 / 미래 날짜 / 시작 > 끝 진도 | 400 | `RECORD400_1` / `_2` / `_3` |
| 과외(없음 · 삭제 · 남의 과외) / 일정 / 교재 없음 | 404 | `RECORD404_1` / `_2` / `_3` |
| 이미 기록 / 진행 중 사이클 없음 | 409 | `RECORD409_1` / `_2` |
| 정산할 정책 없음 (데이터 이상) | 500 | `SETTLEMENT500_1` |

동시 요청은 고려하지 않는다. 같은 회차를 동시에 저장하면 늦은 요청은 유니크 제약으로 `COMMON500`.

---

## 일정

일정 탭(5.1.1). 회차는 모두 공통 예정 회차 계산기(`LessonCalendar`)로 만든다 — 반복 일정을 펼치고 저장된 회차(기록 · 휴강 · 보강)로 덮어쓴다. 대상은 내 과외 중 수업 중(`ACTIVE`) · 삭제 안 됨. 과외 수와 상관없이 쿼리 수가 일정하다(monthly 4 · daily 10, `ScheduleServiceIntegrationTest`).

### 한 달 달력 — `GET /api/v1/schedules/monthly?yearMonth=YYYY-MM` (`200`, `SCHEDULE200_1`)

```json
{
  "yearMonth": "2026-11",
  "today": "2026-11-11",
  "students": [ { "tutoringId": 31, "studentName": "김서아" }, { "tutoringId": 35, "studentName": "이도윤" } ],
  "days": [
    { "date": "2026-11-11", "lessons": [ { "tutoringId": 31, "canceled": true, "makeup": false }, { "tutoringId": 35, "canceled": false, "makeup": false } ], "lessonCount": 2, "eventCategories": [] }
  ]
}
```

- `students`: 범례, tutoringId 오름차순(프론트가 이 순서로 색을 정함). `days`: 수업이 있는 날만 날짜순, `lessons`는 시작 시각순.
- `eventCategories`: 선생님 일정(`teacher_events`, PR-9) 전까지 항상 `[]`.

### 그날 일정 · 수업 — `GET /api/v1/schedules/daily?date=YYYY-MM-DD&tutoringId=` (`200`, `SCHEDULE200_2`)

`date` 없으면 오늘, `tutoringId` 있으면 그 과외만.

```json
{
  "date": "2026-11-11",
  "isToday": true,
  "events": [],
  "sessions": [
    {
      "sessionId": null, "scheduleId": 9, "originalDate": "2026-11-11",
      "date": "2026-11-11", "startTime": "20:30", "endTime": "22:00",
      "tutoringId": 35, "studentName": "이도윤", "subjectName": "영어 · 수학",
      "sessionType": "REGULAR", "status": "SCHEDULED", "cancelReason": null,
      "hasRecord": false, "timeChanged": false, "pendingTimeChange": false, "makeup": null,
      "cycle": { "cycleNo": 1, "noInCycle": 3, "recognizedCount": 2, "settlementSessionCount": 4, "startsNewCycle": false, "isPredicted": true },
      "action": "RECORD"
    }
  ],
  "upcoming": []
}
```

- `sessions`: 그날 수업, 시작 시각순. 저장 전 회차는 `sessionId: null`이고 (`scheduleId`, `originalDate`)로 기록 · 휴강 API에 보낸다.
- `upcoming`: 오늘일 때만 지금 시각 이후 수업 최대 3건(8주 안, 휴강 포함). 오늘이 아니면 `[]`.
- `subjectName`: 과목 순서대로 ` · `로 이음, 기타는 직접 입력한 과목명.
- `cycle`: 기록한 인정 회차는 확정 번호, 지난 미기록은 '지금 기록하면 될 번호'(인정 수 + 1), 앞으로의 예정은 남은 회차를 넘으면 다음 사이클(`startsNewCycle`), 미인정 휴강은 `noInCycle: null`.
- `action`: 기록됨 · 휴강 · 보강 = `NONE`, 오늘 · 지난 미기록 정규 = `RECORD`, 앞으로의 정규 예정 = `ADJUST`. 정산이 끝난 날짜의 미기록 회차는 만들지 않는다.
- 아직 고정값: `events` = `[]`(PR-9), `cancelReason` = `null`(휴강 사유 컬럼 전), `pendingTimeChange` = `false`(시간 변경 전), `makeup` = `null`(보강 전), `timeChanged` = `false`(시간 변경 컬럼 전).

| 상황 | HTTP | code |
| --- | --- | --- |
| `yearMonth` · `date` · `tutoringId` 형식 오류 (`result`에 파라미터 메시지) / `yearMonth` 누락 (`result: null`) | 400 | `COMMON400` |
| `tutoringId`가 내 수업 중 과외가 아님 (Notion `TUTORING404_1`) | 404 | `SCHEDULE404_1` |

---

## 정산 리포트

### 정산 리포트 목록 조회 — `GET /api/v1/reports/deliveries` (`200`, `REPORT200_5`)

리포트 목록 화면(T-13). **내 과외**(삭제되지 않음, 종료 포함)의 리포트를 최근에 만든 순(`reportId` 큰 순)으로 돌려준다. 요청 파라미터 없음, 페이징 없음.

```json
{
  "items": [
    {
      "reportId": 22, "tutoringId": 4, "studentName": "박지후", "settlementMonth": "2026-09",
      "status": "SENT", "sentAt": "2026-10-01T09:20:00", "viewedAt": "2026-10-01T21:05:00",
      "createdAt": "2026-09-30T21:00:00"
    },
    {
      "reportId": 21, "tutoringId": 3, "studentName": "윤서아", "settlementMonth": "2026-09",
      "status": "GENERATED", "sentAt": null, "viewedAt": null, "createdAt": "2026-09-29T20:00:00"
    }
  ]
}
```

- `settlementMonth`: 정산 월(`YYYY-MM`). 사이클 첫 인정 회차의 월이다. 사이클은 직전 정산 순간에 열려 시작 시각이 앞 달일 수 있으므로 시작 시각이 아니라 첫 인정 회차를 기준으로 한다. 인정 회차가 없으면 사이클 시작월.
- `status`: 발송 확인(`POST /send`) 전 `GENERATED`, 후 `SENT` ("보냄" 표시). `sentAt`: 첫 발송 확인 시각, 보내지 않았으면 `null`.
- `viewedAt`: 첫 열람 시각(`settlement_report_views.first_viewed_at`). 학부모가 둘이면 먼저 연 시각. 열람 기록이 없으면 `null` (UT 시점에는 학부모 열람 API가 없어 항상 `null`).
- `createdAt`: 리포트를 만든 시각 (정산 시각).
- 리포트가 없으면 `items`는 `[]`.

### 정산 리포트 조회 — `GET /api/v1/reports/{reportId}` (`200`, `REPORT200_1`)

본인 과외의 리포트만. 네 영역(요약 · 진도 · 코멘트 · 교육비) 중 요약 · 진도를 돌려준다. 통계 · 진도는 정산 시점에 고정되고, `period`만 조회 때 인정 회차로 계산한다.

```json
{
  "reportId": 21,
  "status": "GENERATED",
  "studentName": "윤서아",
  "period": { "startDate": "2026-09-14", "endDate": "2026-09-30" },
  "stats": {
    "completedCount": 8, "targetCount": 8, "canceledCount": 0,
    "homeworkRate": 88, "prevHomeworkRate": 75,
    "understanding": { "high": 5, "medium": 3, "low": 0 }
  },
  "progress": [
    { "textbookName": "수능특강 영어", "startLabel": "3강", "endLabel": "9강", "textbookStatus": "ACTIVE" },
    { "textbookName": "Grammar Zone 기본", "startLabel": "Unit 12", "endLabel": "Unit 20", "textbookStatus": "ACTIVE" }
  ],
  "understandingLevel": "HIGH",
  "parents": [ { "participantId": 12, "label": "MOTHER" } ]
}
```

- `status`: 발송 확인(`POST /send`) 전 `GENERATED`, 후 `SENT`. `textbookStatus`: 교재 추가 · 종료 기능 전까지 `ACTIVE`.
- `period`: 사이클 인정 회차의 첫 · 마지막 수업일. `canceledCount`: 그 기간의 휴강 수.
- `homeworkRate`: (DONE + PARTIAL × 0.5) ÷ 지난 숙제 결과가 있는 기록 수, % 반올림. 결과가 없으면 `null`. `prevHomeworkRate`: 직전 사이클 리포트 값, 없으면 `null`.
- `understandingLevel`: 이해도 수준 `HIGH` / `MEDIUM` / `LOW`. `understanding` 횟수로 조회 때 계산한다. 상 3 · 중 2 · 하 1점 가중평균이 2.5 이상이면 `HIGH`, 1.5 이상이면 `MEDIUM`, 그 아래 `LOW`. 이해도 기록이 0건이면 `null`.
- `parents`: 카톡 공유를 받을 수 있는 학부모(삭제되지 않았고 링크 토큰이 있음, 등록 순). `participantId`를 공유 정보 생성(`POST /shares`)의 받는 사람으로 쓴다. `label`은 `MOTHER` 어머님 / `FATHER` 아버님 / `GUARDIAN` 보호자님. 학부모 링크 화면(`/links/...`)에서는 `[]`.
- `progress`: 교재 ID 순, 사이클 첫 회차 시작 ~ 마지막 회차 끝 진도. 라벨 `Unit 12` · `Chapter 3` · `3단원` · `45p` · `3회`, 직접 입력 단위는 숫자 + 단위(`9강`). 진도 기록이 없는 교재는 빠진다.

에러: `reportId`가 숫자가 아님 `COMMON400` / 없거나 남의 리포트 `REPORT404_1`.

### 코멘트 조회 — `GET /api/v1/reports/{reportId}/comment` (`200`, `REPORT200_2`)

본인 과외의 리포트만. 리포트 네 영역 중 코멘트.

```json
{
  "comment": "이번 달 서아는 리딩튜터 중급 Unit 1부터 16까지 …",
  "commentSource": "AI_DRAFT",
  "reviewed": false,
  "aiStatus": "DONE",
  "softenedSentences": []
}
```

- `comment` · `commentSource`: 코멘트가 아직 없으면 둘 다 `null`. `commentSource`가 `AI_DRAFT`면 'AI 초안' 배지. 선생님이 저장하면 `TEACHER` (`AI_EDITED`는 AI 기능과 함께).
- `reviewed`: 선생님이 코멘트를 확인했는지. `false`면 발송할 수 없다.
- `aiStatus` · `softenedSentences`: AI 기능 전이라 자리만 있다. `aiStatus`는 `commentSource`가 `AI_DRAFT`일 때만 `DONE`이고 그 외 `null`, `softenedSentences`는 항상 `[]` (UT에서 '부드럽게 만든 문장'은 숨김).
- UT에서는 AI 생성을 빼므로 실제 정산으로 만든 리포트는 코멘트가 `null`이고, 시드 리포트에만 AI 초안이 들어 있다.

에러: 없거나 남의 리포트 `REPORT404_1`.

### 코멘트 저장 · 확인 — `PUT /api/v1/reports/{reportId}/comment` (`200`, `REPORT200_3`)

코멘트를 저장하고 확인 처리한다. 출처는 `TEACHER`가 된다. 응답은 코멘트 조회와 같다.

```json
{ "text": "이번 달 서아는 …" }
```

| 필드 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `text` | String | O | 공백 불가, 1000자 이하 |

| 상황 | HTTP | code |
| --- | --- | --- |
| `text` 공백 · 1000자 초과 (`result.text`에 메시지) | 400 | `COMMON400` |
| 없거나 남의 리포트 | 404 | `REPORT404_1` |
| 이미 보낸 리포트 (발송 후 내용 고정) | 409 | `REPORT409_1` |

### 발송 확인 — `POST /api/v1/reports/{reportId}/send` (`200`, `REPORT200_4`)

프론트가 카카오톡 공유 창을 띄운 **뒤** 호출한다. 리포트를 `SENT`로 바꾸고 발송 방법은 `KAKAO_SHARE`로 남긴다. 요청 본문 없음. 같은 리포트로 만든 REQUESTED 공유(`POST /shares`, `SETTLEMENT_REPORT`)도 함께 `SENT`(`sent_via: MANUAL`)로 바꾼다. 카카오 웹훅은 UT 이후.

```json
{ "reportId": 21, "status": "SENT", "sentAt": "2026-10-01T09:20:00" }
```

- 이미 `SENT`면 성공으로 응답하고 `sentAt`은 첫 발송 시각 그대로 (공유를 다시 한 경우).

| 상황 | HTTP | code |
| --- | --- | --- |
| 없거나 남의 리포트 | 404 | `REPORT404_1` |
| 코멘트 확인 전 (`reviewed: false`) | 409 | `REPORT409_2` |

---

## 교육비

선생님 수입 탭. **내 과외**(삭제되지 않음)의 청구만 다룬다. 금액 이름은 공통 '금액 이름' 참고.

### 입금 현황 조회 — `GET /api/v1/invoices?month=YYYY-MM` (`200`, `INVOICE200_1`)

`month` 생략 시 한국 시간 이번 달. 페이징 없음.

- **대상**: 취소(CANCELED)가 아닌 청구 중 ① 안내일(`notified_at`, 없으면 `created_at`)이 그 달 안이거나 ② 그 달 이전이고 `balance`가 남은 것(PENDING · PARTIAL · CONFIRM_REQUESTED). 종료된 과외 포함.
- **정렬**: CONFIRM_REQUESTED → PENDING → PARTIAL → PAID, 같은 상태는 안내일 최신순 → `invoiceId` 큰 순.

```json
{
  "confirmRequestedCount": 1,
  "unpaidCount": 2,
  "summary": { "totalAmount": 720000, "paidAmount": 120000, "unpaidAmount": 600000 },
  "items": [
    {
      "invoiceId": 12, "tutoringId": 3, "studentName": "서아", "invoiceName": "이번 8회차",
      "amount": 360000, "paidAmount": 120000, "balance": 240000, "previousUnpaidAmount": 0,
      "status": "PARTIAL", "statusLine": { "type": "PARTIAL_PAID", "at": "2026-11-15T20:00:00" },
      "reportId": 5, "notifiedAt": "2026-11-10T19:02:11", "createdAt": "2026-11-10T18:40:03",
      "parents": [ { "participantId": 12, "label": "MOTHER" } ]
    }
  ]
}
```

- 건수와 `summary`는 `items`에서 더한다: `confirmRequestedCount` = CONFIRM_REQUESTED 수, `unpaidCount` = 미완료(PENDING · PARTIAL · CONFIRM_REQUESTED) 수, `summary`는 `amount` · `paidAmount` · `balance` 합.
- `studentName`: 과외 삭제 시 `null`. `invoiceName`: FIRST `첫 N회차` / NEXT `다음 N회차` / CURRENT `이번 N회차` / FINAL `마지막 정산` (N = `settlementSessionCount`).
- `reportId`: 함께 보낸 리포트, 선불 첫 청구 등은 `null`. `notifiedAt`: 첫 안내 시각, 안 보냈으면 `null`.
- `parents`: 그 과외의 카톡 공유를 받을 수 있는 학부모. 입금 안내 다시 보내기(`POST /shares`, `PAYMENT_RESEND`)의 받는 사람. 형식은 리포트 조회와 같다. 입금 확인 · 다시 보내기 확인 응답의 `item`에도 있다.
- `statusLine` (위에서 먼저 맞는 것, 계산값):

| 순서 | `type` | 조건 | `at` |
| --- | --- | --- | --- |
| 1 | `PARENT_CONFIRMED` | CONFIRM_REQUESTED | `confirm_requested_at` |
| 2 | `PARTIAL_PAID` | PARTIAL | 마지막 유효 입금 확인 시각 |
| 3 | `NONE` | PAID | `null` |
| 4 | `RESENT` | `renotified_at` 있음 | 다시 보낸 시각 |
| 5 | `REMINDER_SCHEDULED` | 미입금 알림 켜짐 + `reminder_sent_at` 없음 + PENDING | `null` |
| 6 | `NOT_SENT` | `notified_at` 없음 | `null` |
| 7 | `NONE` | 그 외 | `null` |

에러: `month` 형식 오류(`2026-13`, `2026-1` 등) `COMMON400`, `result`는 `{ "month": "월은 YYYY-MM 형식이어야 합니다." }`.

### 입금 확인 — `POST /api/v1/invoices/{invoiceId}/payments` (`201`, `INVOICE201_1`)

선생님이 확인한 입금([전액 입금 완료] · 일부 입금)을 기록한다. 청구 행을 잠그고(FOR UPDATE) 입금 추가와 상태 변경을 한 트랜잭션에서 한다. 합계 ≥ `amount`면 PAID, 아니면 PARTIAL (CONFIRM_REQUESTED에서도 같음). 학부모 알림은 없다.

| 요청 필드 (본문 생략 가능) | 제약 | 설명 |
| --- | --- | --- |
| `amount` | 1 이상 | 없으면 `balance` 전부, `balance`보다 크면 `balance`까지만 |
| `confirmedAt` | `yyyy-MM-ddTHH:mm:ss` (KST) | 입금 확인 시각(월별 합계 기준). 없으면 요청 시각 |

응답 `{ paymentId, appliedAmount, item }` — `item`은 입금 현황 `items[]`와 같은 형식.
예: 360,000원 청구에 `120000` → PARTIAL, `balance` 240000. 이어서 `300000` → `appliedAmount` 240000, PAID.
`Idempotency-Key`: **선택** (프론트 적용 후 필수로 바꾼다). 보내면 위 '멱등 키' 규칙대로 같은 키의 재요청은 입금을 다시 기록하지 않는다. 보내지 않으면 같은 요청을 두 번 보낼 때 두 번 기록된다(잔액은 넘지 않음).

| 상황 | HTTP | code |
| --- | --- | --- |
| `amount` ≤ 0 (`result: { "amount": "입금 금액은 1원 이상이어야 합니다." }`), 깨진 JSON | 400 | `COMMON400` |
| 청구 없음 · 남의 과외 · 삭제된 과외 | 404 | `INVOICE404_1` |
| 취소된 청구 / 이미 입금 완료 | 409 | `INVOICE409_1` / `INVOICE409_3` |

### 입금 안내 다시 보내기 확인 — `POST /api/v1/invoices/{invoiceId}/renotifications` (`200`, `INVOICE200_4`)

프론트가 [카톡으로 입금 안내 다시 보내기]의 카카오톡 공유 창을 띄운 **뒤** 호출한다. 청구 행을 잠그고 `renotified_at`을 요청 시각으로 남긴다(마지막 시각으로 덮어씀). 요청 본문 없음. 같은 청구로 만든 REQUESTED 공유(`POST /shares`, `PAYMENT_RESEND`)도 함께 `SENT`(`sent_via: MANUAL`)로 바꾼다. 카카오 웹훅은 UT 이후.

응답은 입금 현황 `items[]` 한 줄과 같은 형식이다. 입금 대기(PENDING)면 `statusLine`이 `{ "type": "RESENT", "at": 다시 보낸 시각 }`('카톡으로 입금 안내를 다시 보냈어요')이 된다. 확인 대기 · 일부 입금은 `statusLine` 순서상 `PARENT_CONFIRMED` · `PARTIAL_PAID`가 먼저 보인다.

| 상황 | HTTP | code |
| --- | --- | --- |
| 청구 없음 · 남의 과외 · 삭제된 과외 | 404 | `INVOICE404_1` |
| 취소된 청구 / 이미 입금 완료 | 409 | `INVOICE409_1` / `INVOICE409_3` |

### 정산 리포트 교육비 안내 조회 — `GET /api/v1/reports/{reportId}/invoice` (`200`, `INVOICE200_2`)

T-14 정산 리포트의 교육비 안내 블록. `invoices.report_id = reportId`인 취소 안 된 청구 1건을 돌려준다. 리포트는 청구 ID를 갖지 않으므로 청구 쪽에서 바로 찾는다. 요청 본문 없음.

```json
{
  "invoiceId": 301,
  "paymentTiming": "PREPAID",
  "settlementSessionCnt": 8,
  "billedCycleStartDate": "2026-09-14",
  "unitPrice": 45000,
  "canceledCount": 0,
  "deductionAmount": 0,
  "calculatedAmount": 360000,
  "amount": 360000,
  "recognizedCount": 8,
  "bankCode": "004",
  "bankName": "국민은행",
  "accountLast4": "1492",
  "accountHolder": "한서윤"
}
```

- 키 이름 · 순서는 노션 명세와 같다. `settlementSessionCnt` · `paymentTiming` · `unitPrice` · `calculatedAmount` · `amount`는 청구 컬럼 값이다.
- `recognizedCount`: '인정 회차 × 회당 단가' 줄의 회차. 정산 없이 만든 선불 첫 청구(FIRST)는 null이고, 이때 프론트는 `settlementSessionCnt`를 보여 준다.
- 입금 계좌(`bankCode` · `bankName` · `accountLast4` · `accountHolder`)는 청구 시점 스냅샷이라 이후 과외 계좌를 바꿔도 이 청구는 그대로다. `bankCode`는 금융결제원 3자리, 목록에 없는 '기타' 은행이면 null이고 `bankName`에 직접 입력한 이름이 온다. 짧은 이름('국민') · 로고는 프론트가 `bankCode`로 찾는다. 전체 계좌번호는 내려주지 않는다(화면은 `****` + 끝 4자리).
- `canceledCount`: 청구 스냅샷 휴강 내역(`canceledLessons`) 개수. `deductionAmount` · `billedCycleStartDate`는 공통 '금액 이름' 참고.
- 예: 후불 회차 부족(인정 5 · 정산 8 · 단가 45,000 · 자동 계산 225,000) → `deductionAmount` 135,000. 단가 버림(수업료 100,000 · 3회 → 단가 33,333)은 3 × 33,333 < 100,000이어도 0.

| 상황 | HTTP | code |
| --- | --- | --- |
| `reportId`가 숫자가 아님 | 400 | `COMMON400` |
| 리포트에 취소 안 된 청구가 없음 · 남의 과외 · 삭제된 과외의 리포트 | 404 | `INVOICE404_1` |

### 교육비 수정 — `POST /api/v1/invoices/{invoiceId}/adjustments` (`200`, `INVOICE200_3`)

T-14 교육비 수정 시트 [저장]. 청구 행을 잠그고(FOR UPDATE) 금액 변경과 수정 이력 1행(`invoice_amount_adjustments`, `adjusted_by = TEACHER`)을 한 트랜잭션에서 남긴다. `calculatedAmount`는 바뀌지 않는다. 같은 금액이면 아무것도 바꾸지 않고 200. 상태는 입금 합계로 다시 정하되 확인 대기(CONFIRM_REQUESTED)는 바뀐 금액을 다 받은 경우가 아니면 유지한다. 학부모 알림 · 리포트 다시 만들기는 없다.

| 요청 필드 | 필수 | 제약 |
| --- | --- | --- |
| `amount` | O | 0 이상 10,000,000 이하 (원) |
| `reason` | O | 공백 아님, 200자 이내. 교육비 카드 '금액 수정 사유' |

```json
{ "amount": 375000, "reason": "교재비 15,000원 추가" }
```

응답은 교육비 안내 조회와 같은 구조다. 예: 360,000원 청구를 위 요청으로 수정 → `amount` 375000, `calculatedAmount` 360000.

| 상황 | HTTP | code |
| --- | --- | --- |
| 필드 검증 실패 (`result`에 필드별 메시지, 예: `{ "amount": "교육비는 0원 이상이어야 합니다." }`), 깨진 JSON | 400 | `COMMON400` |
| 청구 없음 · 남의 과외 · 삭제된 과외 | 404 | `INVOICE404_1` |
| 취소된 청구 / 이미 받은 금액보다 적게 / 함께 보낸 리포트가 발송 완료(SENT) | 409 | `INVOICE409_1` / `INVOICE409_2` / `INVOICE409_4` |

---

## 카카오톡 공유

학부모 · 학생에게 가는 메시지는 모두 선생님의 카카오톡 공유로 나간다. 흐름: ① `POST /shares`로 메시지를 받고 공유 기록을 `REQUESTED`로 남긴다 → ② 프론트가 카카오 공유 창을 띄운다 → ③ 보낸 뒤 원인 데이터의 확인 API(리포트 `POST /reports/{id}/send`, 입금 안내 `POST /invoices/{id}/renotifications`)를 부르면 원인이 바뀌고 그 공유도 `SENT`(`MANUAL`)가 된다. 카카오 웹훅(`WEBHOOK`)은 UT 이후.

### 공유 정보 생성 — `POST /api/v1/shares` (`201`, `SHARE201_1`)

```json
{ "kind": "SETTLEMENT_REPORT", "sourceId": 21, "participantId": 12 }
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `kind` | String | O | 공유 종류. 지금은 `SETTLEMENT_REPORT`(정산 리포트) · `PAYMENT_RESEND`(입금 안내 다시)만 지원 |
| `sourceId` | Long | 지원 종류는 O | `SETTLEMENT_REPORT` = 리포트 ID, `PAYMENT_RESEND` = 청구 ID |
| `participantId` | Long | O | 받는 사람 (그 과외의 학부모). 리포트 조회 · 입금 현황의 `parents[].participantId` |

```json
{
  "shareId": 501,
  "url": "https://sudam.app/l/{토큰}/reports/21",
  "title": "윤서아 학생 8회차 정산 리포트",
  "description": "수업 8/8회 · 숙제 수행률 88% · 다음 8회차 교육비 360,000원",
  "buttonTitle": "링크로 보기"
}
```

- `url`: 받는 사람의 링크 화면. `{app.link.base-url}/l/{token}/reports/{reportId}`(리포트) · `/l/{token}/invoices/{invoiceId}`(교육비). base-url은 로컬 `http://localhost:8081`, 개발 서버 `${FRONT_BASE_URL}`. 토큰은 참여자마다 하나다.
- `SETTLEMENT_REPORT`: 제목 `{학생} 학생 {정산 회차}회차 정산 리포트`, 설명 `수업 {진행}/{정산}회 · 숙제 수행률 {n}% · {청구 이름} 교육비 {금액}원`(숙제 결과 · 함께 보낸 청구가 없으면 그 부분은 뺀다), 버튼 `링크로 보기`. **코멘트를 확인한 리포트만** 공유할 수 있다(발송 확인과 같은 규칙). 이미 보낸 리포트도 다시 공유할 수 있다.
- `PAYMENT_RESEND`: 제목 `{학생} 학생 교육비 안내`, 설명 `{청구 이름} 교육비 {금액}원`(일부 입금이면 ` · 남은 금액 {잔액}원`), 버튼 `교육비 보기`. 학부모 교육비 화면 API는 아직 없다.
- 같은 원인 · 같은 받는 사람의 이전 `REQUESTED`는 `DISMISSED`로 바뀐다 (공유 창을 여러 번 열어도 할 일은 1건).
- 받는 사람: 그 과외의 삭제되지 않은 학부모이고 링크 토큰이 있어야 한다. 받지 않기(`receive_method = NONE`) · 수신 거부(`notification_consent = REJECTED`)면 보낼 수 없다. 초대 수락 대기(`PENDING`)는 보낼 수 있다.

| 상황 | HTTP | code |
| --- | --- | --- |
| `kind` · `participantId` 없음(`result`에 필드별 메시지), 없는 `kind` 값, 깨진 JSON | 400 | `COMMON400` |
| 아직 지원하지 않는 종류 / `sourceId` 없음 | 400 | `SHARE400_1` / `SHARE400_2` |
| 받는 사람이 없음 · 다른 과외 · 삭제됨 · 학부모 아님 · 링크 없음 | 404 | `SHARE404_1` |
| 리포트 없음 · 남의 리포트 / 청구 없음 · 남의 청구 | 404 | `REPORT404_1` / `INVOICE404_1` |
| 코멘트 확인 전 리포트 | 409 | `REPORT409_2` |
| 취소된 청구 / 입금 완료된 청구 | 409 | `INVOICE409_1` / `INVOICE409_3` |
| 수신 거부한 받는 사람 | 409 | `SHARE409_1` |

---

## 학부모 링크

학부모가 카카오톡 메시지의 링크로 여는 화면용 API. **로그인 없이** 경로의 참여자 링크 토큰(`participants.link_token`)으로 본인을 확인한다 (`SecurityConfig.allowUris`의 `/api/v1/links/**`). `Authorization` 헤더를 보내지 않는다.

링크 확인 (ERD 9.1.1): 토큰으로 참여자 조회 → 삭제된 참여자 · 삭제된 과외 · 만료(`link_expires_at` 지남, 진행 중 과외는 만료 없음) · 학부모가 아님이면 모두 `PARTICIPANT404_1`. 어느 경우인지 구분해 알려 주지 않는다. 첫 입장 수신 동의(9.1.2) · 없는 토큰 반복 요청 제한은 아직 없다.

### 학부모 정산 리포트 열기 — `GET /api/v1/links/{token}/reports/{reportId}` (`200`, `REPORT200_6`)

그 학부모 과외의 **보낸(SENT)** 리포트만 열린다. 처음 열면 `settlement_report_views`에 첫 열람 시각을 남겨 선생님 리포트 목록의 `viewedAt`에 나타난다. 다시 열어도 첫 열람 시각은 바뀌지 않는다.

```json
{
  "report": { "reportId": 21, "status": "SENT", "studentName": "윤서아", "period": { … }, "stats": { … }, "progress": [ … ], "understandingLevel": "MEDIUM", "parents": [] },
  "comment": "이번 달 서아는 …",
  "invoice": { "invoiceId": 2, "paymentTiming": "PREPAID", "settlementSessionCnt": 8, "amount": 360000, "recognizedCount": 8,
               "bankCode": "004", "bankName": "국민은행", "accountLast4": "1492", "accountHolder": "한서윤", … },
  "teacherName": "한서윤",
  "sentAt": "2026-10-01T09:20:00"
}
```

- `report`: 선생님 `GET /reports/{reportId}`와 같은 형식이고 `parents`는 `[]`.
- `invoice`: 리포트와 함께 보낸 교육비 안내(취소되지 않은 청구). 선생님 `GET /reports/{reportId}/invoice`와 같은 형식이다. 리포트 화면이라 계좌번호는 **끝 4자리만** 담는다(ERD: 전체 번호는 학부모 교육비 화면의 [계좌 복사]에만). 함께 보낸 청구가 없으면 `null`.
- 응답은 DTO로만 만든다. 코멘트 출처 · 확인 시각 · 발송 방법 같은 선생님용 값은 담지 않는다.

| 상황 | HTTP | code |
| --- | --- | --- |
| `reportId`가 숫자가 아님 | 400 | `COMMON400` |
| 링크가 없거나 만료 · 폐기, 삭제된 참여자 · 과외, 학부모가 아님 | 404 | `PARTICIPANT404_1` |
| 리포트가 없음 · 다른 과외의 리포트 · 아직 보내지 않음 | 404 | `REPORT404_1` |

---

<!--
새 엔드포인트 틀: ### <기능> — `METHOD /api/v1/<자원>` (`상태`, `성공 코드`)
요청 필드 표(필드 · 필수 · 제약) → 응답 예시 JSON + 필드 설명 bullet → 에러 표(상황 · HTTP · code)
-->
