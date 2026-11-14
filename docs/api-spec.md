# API 명세

> 최종 수정: 2026-11-14 · 규칙: `.claude/rules/api-convention.md`, `.claude/rules/error-code-convention.md`

## 공통

- Base URL(로컬) `http://localhost:8080`, prefix `/api/v1`, `application/json;charset=UTF-8`
- 인증 불필요 경로: `/oauth2/**`, `/login/**`, `/api/v1/auth/reissue`, Swagger(`/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`). 그 외는 `Authorization: Bearer {AT}` 필요.
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
| `RECORD201_1` | 201 | 수업 기록을 저장했습니다. |
| `REPORT200_1` | 200 | 정산 리포트를 조회했습니다. |
| `INVOICE200_1` | 200 | 입금 현황이 성공적으로 조회되었습니다. |
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
| `SETTLEMENT500_1` | 500 | 정산에 적용할 과외 정책을 찾을 수 없습니다. |
| `REPORT404_1` | 404 | 정산 리포트를 찾을 수 없습니다. |
| `INVOICE404_1` | 404 | 교육비 안내를 찾을 수 없습니다. |
| `INVOICE409_1` | 409 | 취소된 교육비 안내입니다. |
| `INVOICE409_2` | 409 | 이미 받은 금액보다 적게 바꿀 수 없습니다. |
| `INVOICE409_3` | 409 | 이미 입금 완료된 교육비입니다. |
| `INVOICE409_4` | 409 | 이미 보낸 정산 리포트의 교육비는 바꿀 수 없습니다. |
| `INVOICE409_5` | 409 | 입금 확인을 요청할 수 없는 상태입니다. |

- `*404_1`(과외 · 리포트 · 청구)은 대상이 없을 때와 **다른 선생님의 것**일 때 모두 쓴다 (존재 여부를 드러내지 않음).
- `INVOICE409_2` · `409_4` · `409_5`는 아직 없는 교육비 수정 · 학부모 [입금했어요] API용이다.

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

---

## 인증

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/oauth2/authorization/kakao` | 카카오 로그인 시작 (Spring Security 제공) |
| `GET` | `/login/oauth2/code/kakao` | 카카오 콜백 |
| `POST` | `/api/v1/auth/reissue` | 토큰 재발급 (공개) |
| `POST` | `/api/v1/auth/logout` | 로그아웃 |

**카카오 콜백** — 필수 동의: `profile_nickname`, `account_email` (누락 시 `AUTH401_1`). 리다이렉트 대상은 `app.oauth2.success-redirect-uri` / `failure-redirect-uri`.
- 성공: `302 {성공 URI}#accessToken={AT}` + `Set-Cookie: refreshToken={RT}; Path=/; Max-Age=1209600; HttpOnly; SameSite=Lax`
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

**약관 동의** — 요청 `{ termsCodes: ["SERVICE", "PRIVACY", "CONTACT"] }` (1개 이상, 중복 무시). 각 코드의 현재 최신 버전에 동의 기록. 응답 `{ agreements: [{ code, agreedAt }] }`. 멱등(이미 동의했으면 기존 `agreedAt`). 약관 행은 `data.sql` 시드.
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

## 정산 리포트

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
  ]
}
```

- `status`: 발송 기능 전까지 `GENERATED`. `textbookStatus`: 교재 추가 · 종료 기능 전까지 `ACTIVE`.
- `period`: 사이클 인정 회차의 첫 · 마지막 수업일. `canceledCount`: 그 기간의 휴강 수.
- `homeworkRate`: (DONE + PARTIAL × 0.5) ÷ 지난 숙제 결과가 있는 기록 수, % 반올림. 결과가 없으면 `null`. `prevHomeworkRate`: 직전 사이클 리포트 값, 없으면 `null`.
- `progress`: 교재 ID 순, 사이클 첫 회차 시작 ~ 마지막 회차 끝 진도. 라벨 `Unit 12` · `Chapter 3` · `3단원` · `45p` · `3회`, 직접 입력 단위는 숫자 + 단위(`9강`). 진도 기록이 없는 교재는 빠진다.

에러: `reportId`가 숫자가 아님 `COMMON400` / 없거나 남의 리포트 `REPORT404_1`.

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
      "reportId": 5, "notifiedAt": "2026-11-10T19:02:11", "createdAt": "2026-11-10T18:40:03"
    }
  ]
}
```

- 건수와 `summary`는 `items`에서 더한다: `confirmRequestedCount` = CONFIRM_REQUESTED 수, `unpaidCount` = 미완료(PENDING · PARTIAL · CONFIRM_REQUESTED) 수, `summary`는 `amount` · `paidAmount` · `balance` 합.
- `studentName`: 과외 삭제 시 `null`. `invoiceName`: FIRST `첫 N회차` / NEXT `다음 N회차` / CURRENT `이번 N회차` / FINAL `마지막 정산` (N = `settlementSessionCount`).
- `reportId`: 함께 보낸 리포트, 선불 첫 청구 등은 `null`. `notifiedAt`: 첫 안내 시각, 안 보냈으면 `null`.
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
`Idempotency-Key`는 아직 없어 같은 요청을 두 번 보내면 두 번 기록된다(잔액은 넘지 않음).

| 상황 | HTTP | code |
| --- | --- | --- |
| `amount` ≤ 0 (`result: { "amount": "입금 금액은 1원 이상이어야 합니다." }`), 깨진 JSON | 400 | `COMMON400` |
| 청구 없음 · 남의 과외 · 삭제된 과외 | 404 | `INVOICE404_1` |
| 취소된 청구 / 이미 입금 완료 | 409 | `INVOICE409_1` / `INVOICE409_3` |

---

<!--
새 엔드포인트 틀: ### <기능> — `METHOD /api/v1/<자원>` (`상태`, `성공 코드`)
요청 필드 표(필드 · 필수 · 제약) → 응답 예시 JSON + 필드 설명 bullet → 에러 표(상황 · HTTP · code)
-->
