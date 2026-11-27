# 아키텍처

> 최종 수정: 2026-11-14
> 전체 구조와 요청 처리 흐름. 세부 규칙은 `.claude/rules/`, 테이블은 `docs/erd.md`, 엔드포인트는 `docs/api-spec.md`.

## 기술 스택

Java 21 · Spring Boot 4.1.1 · Gradle 9.8.1 · MySQL 8 + Spring Data JPA(Hibernate) · Redis(토큰 저장소) · Spring Security OAuth2 Client(카카오) + JWT · Bean Validation · Lombok · springdoc(Swagger).

Boot 4라서 스타터 이름이 Boot 3와 다르다 (`spring-boot-starter-webmvc`, `...-webmvc-test`, `...-data-jpa-test`). `CLAUDE.md` 참고.

## 패키지 구조

```
com.sudam
├── SudamApplication.java
├── domain                              # 도메인별 수직 분할
│   ├── auth                            # 토큰 재발급 · 로그아웃
│   │   ├── controller / service
│   │   ├── dto                         # TokenPair(서비스 반환형), response/TokenReissueResponse
│   │   └── exception                   # AuthException, code/AuthErrorCode · AuthSuccessCode
│   ├── user                            # 회원 · 온보딩(약관 동의 → 프로필) · 설정 · 탈퇴
│   │   ├── controller / service
│   │   ├── entity                      # User, Terms(시드 전용), UserTermsAgreement, TeacherProfile(PK = users.id)
│   │   ├── enums                       # Role, Provider, Subject(과외도 공유), TermsCode, UserStatus
│   │   ├── repository                  # 엔티티별 JpaRepository
│   │   ├── dto                         # request/AgreeTermsRequest · SaveProfileRequest, response/*
│   │   └── exception                   # UserException, code/UserErrorCode · UserSuccessCode
│   ├── tutoring                        # 과외 (엔티티 · 레포지토리만, API는 아직 없다)
│   │   ├── entity                      # Tutoring, TutoringSchedule, Textbook, TutoringPolicy, TutoringSetting
│   │   │   └── TutoringPolicies.java   # 사이클 N에 적용할 정책 행을 고르는 순수 함수
│   │   ├── enums                       # TutoringStatus, ProgressUnit, PaymentTiming, PresetType, ParentUpdateFrequency
│   │   └── repository                  # TutoringRepository.findByIdForUpdate = 사이클 잠금 기준 (사이클을 열고 닫기 전 과외 행 잠금)
│   ├── participant                     # 과외에 속한 학부모 · 학생 (엔티티 · 레포지토리만)
│   │   ├── entity/Participant.java
│   │   ├── enums                       # ParticipantRole, ParticipantLabel, ReceiveMethod, NotificationConsent
│   │   └── repository
│   ├── session                         # 수업 회차
│   │   ├── controller                  # GET /api/v1/sessions/today (홈 오늘 수업, TodaySessionService)
│   │   ├── entity/Session.java         # completeRegular = 정규 회차 기록
│   │   ├── enums                       # SessionType, SessionStatus
│   │   ├── repository                  # 일정 · 날짜 중복 확인, 사이클 인정 회차의 정렬 조회 · 수 · 첫/마지막 날짜, 날짜 범위 휴강, 계산기용 기간 · 인정 회차 · 정산 날짜 조회
│   │   └── service
│   │       ├── SessionCycleReader      # 정산 포트 CycleSessionReader 구현 (MANDATORY)
│   │       ├── TodaySessionService     # 홈 오늘 수업 목록 (계산기 결과를 카드로 조립)
│   │       └── LessonCalendar          # 공통 예정 회차 계산기 (LessonExpander 펼치기 · CycleTimeline 사이클 예측, 결과는 LessonSlot)
│   ├── schedule                        # 일정 탭 조회 (계산기 위의 얇은 조회, 엔티티 없음)
│   │   ├── controller / service        # GET /api/v1/schedules/monthly · daily (ScheduleService)
│   │   ├── enums                       # LessonAction (RECORD · ADJUST · NONE)
│   │   ├── dto/response                # MonthlyScheduleResponse, DailyScheduleResponse
│   │   └── exception                   # ScheduleException, code/ScheduleErrorCode · ScheduleSuccessCode
│   ├── record                          # 수업 기록 · 회차 진도
│   │   ├── controller                  # TutoringRecordController: GET .../record-form, POST .../records (과외 아래 하위 자원)
│   │   ├── service                     # TutoringRecordService(기록 저장 → 정산), RecordFormService(기록 화면, 예정 회차 계산기 LessonCalendar 사용)
│   │   ├── entity                      # SessionRecord, SessionProgress (둘 다 회차를 가리킴)
│   │   ├── enums                       # Understanding, HomeworkResult
│   │   ├── repository                  # TextbookLastPosition: 지난 진도 조회 결과 타입
│   │   ├── dto                         # request/SaveRecordRequest, response/SaveRecordResponse · RecordFormResponse
│   │   └── exception                   # RecordException, code/RecordErrorCode · RecordSuccessCode
│   ├── report                          # 정산 리포트 (요약 · 진도)
│   │   ├── controller / service        # GET /api/v1/reports/{reportId} (ReportService)
│   │   ├── service                     # SettlementReportFactory = 정산 포트 SettlementReportCreator 구현 (MANDATORY), ReportCalculator(순수 함수: 수행률 · 진도 라벨)
│   │   ├── entity                      # SettlementReport(통계 스냅샷), SettlementReportProgress(교재별 진도 표)
│   │   ├── enums                       # ReportStatus, ReportTextbookStatus
│   │   ├── repository
│   │   ├── dto/response/ReportResponse
│   │   └── exception                   # ReportException, code/ReportErrorCode · ReportSuccessCode
│   ├── cycle                           # 수업 사이클 = 정산 단위 묶음
│   │   ├── entity/Cycle.java           # open / settle / isInProgress
│   │   ├── enums/CycleStatus.java      # IN_PROGRESS, SETTLED
│   │   ├── repository                  # CycleRepository: 진행 중 · 번호별 · 마지막 · 과외별 목록 조회
│   │   ├── service/CycleOpener.java    # openNext: 사이클을 여는 유일한 창구 (MANDATORY). 과외 만들기 · 정산이 부른다
│   │   └── exception                   # CycleException, code/CycleErrorCode (CYCLE409_1, CYCLE409_2)
│   ├── invoice                         # 교육비 청구 · 입금 · 금액 수정 이력 (엔티티 · 레포지토리만)
│   │   ├── entity                      # Invoice, InvoicePayment, InvoiceAmountAdjustment
│   │   │   └── InvoiceSnapshot.java    # 계산 근거 JSON 값 객체 (CanceledLesson, RecordedPayment 포함)
│   │   ├── enums                       # InvoiceKind, InvoiceStatus, AdjustedBy
│   │   ├── repository                  # InvoiceRepository는 잠금 조회 findByIdForUpdate 제공
│   │   └── exception                   # InvoiceException, code/InvoiceErrorCode
│   ├── share                           # 카카오톡 공유 기록(link_shares). POST /shares가 메시지를 만들고 REQUESTED를 남긴다
│   │   ├── service                     # ShareService(공유 정보 생성), LinkShareRecorder(확인 API가 부르는 SENT 처리, MANDATORY), ShareLinkFactory(링크 URL)
│   │   ├── entity/LinkShare.java       # request(받는 사람, 종류, 원인)
│   │   └── exception                   # ShareException, code/ShareErrorCode · ShareSuccessCode
│   └── settlement                      # 정산 실행 (컨트롤러 없음. 기록 저장이 같은 트랜잭션에서 호출)
│       ├── service                     # SettlementService.settleIfReached (MANDATORY), InvoiceAmountCalculator(순수 함수)
│       ├── dto/SettlementResult        # 기록 저장 응답의 settlement { settled, cycleId, reportId, invoiceId }
│       ├── port                        # 정산이 쓰는 인터페이스: CycleSessionReader(+CyclePeriod, 구현은 session), SettlementReportCreator(구현은 report)
│       └── exception                   # SettlementException, code/SettlementErrorCode (SETTLEMENT500_1)
└── global                              # 공통 관심사
    ├── ApiResponse.java                # 모든 응답의 래퍼
    ├── exception                       # ProjectException, code/Base*Code · General*Code
    ├── handler/GeneralExceptionHandler # @RestControllerAdvice
    ├── entity/BaseTimeEntity.java      # createdAt / updatedAt 감사
    ├── crypto/AccountNumberConverter   # 계좌번호 AES-256-GCM JPA 변환기
    ├── seed                            # 시연용 가상 데이터 (sudam.seed.enabled = true일 때만 빈 생성). DemoSeedRunner → DemoSeedService(→ DemoSeedResetter: sudam.seed.reset일 때 삭제), DemoIncomeCard · DemoLessonPlan(순수 함수)
    ├── config                          # SecurityConfig, JpaConfig, SwaggerConfig, ClockConfig(Asia/Seoul Clock 빈 — 서비스는 LocalDateTime.now(clock))
    └── security                        # 인증 인프라 (코드 enum은 domain/auth에 둔다)
        ├── filter/JwtAuthenticationFilter
        ├── token/TokenStore            # Redis — rt:{userId}, bl:{jti}
        ├── util                        # JwtUtil, CookieUtil
        ├── exception                   # EntryPoint(401), AccessDeniedHandler(403)
        └── oauth2                      # CustomOAuth2User(principal), CustomOAuth2UserService, KakaoResponse, 성공 · 실패 핸들러
```

새 도메인은 `domain.<도메인>` 아래에 `controller / service / repository / entity / dto / enums / exception(+code)`를 둔다. 성공 · 에러 코드 enum은 항상 `<스코프>/exception/code/`, 예외 클래스는 그 한 단계 위다.

### 코드 현황

| 스코프 | 성공 | 에러 |
| --- | --- | --- |
| COMMON | 200, 201 | 400 · 401 · 403 · 404 · 405 · 406 · 409 · 413 · 415 · 500 · 503 |
| AUTH | 200_1 ~ 200_2 | 400_1, 401_1 ~ 401_6 |
| USER | 200_1 ~ 200_4, 201_1 ~ 201_2 | 400_1 ~ 400_2, 404_1 ~ 404_2, 409_1 |
| INVOICE | 200_1 ~ 200_4, 201_1 | 404_1, 409_1 ~ 409_5 |
| REPORT | 200_1 ~ 200_6 | 404_1, 409_1 ~ 409_2 |
| RECORD | 201_1 | 400_1 ~ 400_3, 404_1 ~ 404_3, 409_1 ~ 409_2 |
| PARTICIPANT | — | 404_1 (학부모 링크) |
| SHARE | 201_1 | 400_1 ~ 400_2, 404_1, 409_1 |
| SETTLEMENT | — | 500_1 |

## 요청 처리 흐름

API 경로는 모두 `/api/v1` 아래다.

### 일반 API

```
SecurityFilterChain(STATELESS) → Controller(@Valid, 응답 조립) → Service(@Transactional, 규칙, DTO 변환) → Repository
```

- 예외는 어디서 던져도 `GeneralExceptionHandler`가 `ApiResponse.onFailure`로 바꾼다. 컨트롤러에서 에러 응답을 직접 만들지 않는다.
- `open-in-view: false`라 지연 로딩은 서비스 안에서 끝낸다.
- 201 Created만 `ResponseEntity`로 감싼다.

### 소셜 로그인

```
GET /oauth2/authorization/kakao → 카카오 → GET /login/oauth2/code/kakao
  → CustomOAuth2UserService.loadUser   KakaoResponse 파싱, 필수 동의 누락 → AUTH401_1, UserService.findOrCreate
  → 성공: AT·RT 발급, rt:{userId} 저장(14일), 302 {프론트}#accessToken={AT} + Set-Cookie refreshToken(HttpOnly)
  → 실패: 302 {프론트}#code={에러 코드}
```

필터 단계 예외는 `@RestControllerAdvice`에 오지 않고 `OAuth2LoginFailureHandler`가 처리한다.

### 인증 · 토큰

```
Authorization: Bearer {AT} → JwtAuthenticationFilter
  서명 · 만료 · typ=access 실패 → AUTH401_3 / bl:{jti}에 있음 → AUTH401_4 / 통과 → CustomOAuth2User(userId, Role)

POST /api/v1/auth/reissue (permitAll, Cookie refreshToken)   RTR
  rt:{userId} 없음 → AUTH401_5 / 불일치 → rt 삭제 후 AUTH401_6 / 일치 → 새 AT·RT, rt 덮어쓰기

POST /api/v1/auth/logout, DELETE /api/v1/users/me
  rt:{userId} 삭제, bl:{jti} 등록(TTL = AT 남은 시간), 쿠키 삭제. 탈퇴는 withdrawn_at 기록 + email · provider_id NULL
```

### 온보딩 · 설정

```
GET  /api/v1/users/me                   status 계산(저장 안 함): 필수 약관 미동의 → TERMS_REQUIRED, 프로필 없음 → PROFILE_REQUIRED, 그 외 ACTIVE
POST /api/v1/users/me/terms-agreements  현재 시행 중인(effective_at <= now, code별 최신 version) 필수 약관이 빠지면 USER400_1. 멱등
POST /api/v1/users/me/profile           이미 있으면 USER409_1. OTHER 포함 시 subjectOther 필수(USER400_2)
PUT  /api/v1/users/me/profile           없으면 USER404_2. 전체 교체 후 flush로 updated_at 확정
GET  /api/v1/users/me/settings          profile · provider만 DB 값, 소개서 · 결제 · 알림은 고정 기본값
```

과목은 `Subject` enum이고 `teacher_profiles.subjects` · `tutorings.subjects`는 enum 이름 배열 JSON 컬럼이다. `OTHER`일 때만 각 테이블의 `subject_other`를 쓴다.

## 설정 / 실행 전제

- `application.yaml`이 `optional:file:.env[.properties]`를 import한다. 키: `DB_URL` `DB_USERNAME` `DB_PASSWORD` `KAKAO_CLIENT_ID` `KAKAO_CLIENT_SECRET` `JWT_SECRET` `ACCOUNT_ENCRYPTION_KEY`(Base64 32바이트, 없거나 길이가 틀리면 기동 실패). Redis 호스트 · 포트와 리다이렉트 URI는 `application-local.yaml`.
- MySQL과 Redis가 모두 떠 있어야 한다. Redis가 없으면 부팅은 되지만 로그인과 인증 요청이 깨진다.
- JPA: `ddl-auto: validate`(스키마는 Flyway가 만든다. 아래 'DB 스키마 (Flyway)'), `open-in-view: false`, `default_batch_fetch_size: 100`.
- 약관: 3행(SERVICE · PRIVACY · CONTACT, 모두 필수)을 환경마다 직접 INSERT한다. 코드나 마이그레이션으로 만들지 않는다. 내용이 바뀌면 같은 `code`의 `version`을 올린 행을 추가한다.
- Swagger: `/swagger-ui.html`, `/v3/api-docs`. `@Tag` · `@Operation` 요약만 담고 에러 코드별 응답은 `docs/api-spec.md`가 기준이다.
- 토큰 만료: AT 30분, RT 14일 (`application.yaml`의 `jwt.*`).

### DB 스키마 (Flyway)

부팅 때 `src/main/resources/db/migration`의 SQL 중 아직 적용되지 않은 것을 버전 순서로 적용하고 `flyway_schema_history`에 기록한다. 그 뒤 Hibernate가 엔티티와 맞는지 검사한다(`validate`). 마이그레이션 작성 규칙은 `.claude/rules/jpa-convention.md`.

**로컬 DB 처음 만들기 · 초기화** — Flyway 도입 전부터 쓰던 로컬 DB는 한 번 비워야 한다. 로컬 DB에서만 실행한다.

1. 로컬 MySQL에서 스키마를 다시 만든다.

   ```sql
   DROP DATABASE IF EXISTS sudam;
   CREATE DATABASE sudam CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   ```

2. 로컬 Redis를 비운다 (`docker exec redis redis-cli FLUSHDB`). 회원 ID가 1부터 다시 매겨져 옛 토큰이 다른 회원의 것이 될 수 있다.
3. 시드를 끄고(`SUDAM_SEED_ENABLED=false`) 애플리케이션을 켠다. V1부터 적용된다.
4. 약관을 넣는다. 시드는 그 뒤에 켠다.

**약관 INSERT** — 약관이 없으면 에러 없이 필수 약관이 0개로 처리돼 `TERMS_REQUIRED`를 건너뛴다. 사용자가 접속하거나 시드를 켜기 전에 넣는다.

```sql
INSERT INTO terms (code, version, title, is_required, effective_at) VALUES
    ('SERVICE', 1, '서비스 이용약관', true, '2026-01-01 00:00:00'),
    ('PRIVACY', 1, '개인정보 수집 · 이용', true, '2026-01-01 00:00:00'),
    ('CONTACT', 1, '학부모 · 학생 연락처 처리', true, '2026-01-01 00:00:00');
```

**개발 서버를 Flyway로 전환 (최초 1회)** — `dev`에 push되면 자동 배포되므로 순서를 지킨다. 기존 데이터는 모두 사라진다.

1. 팀에 공지하고 RDS 스냅샷을 만든다. `ENV_VARS`에서 `SUDAM_SEED_ENABLED` · `SUDAM_SEED_RESET`을 끄고 `JWT_SECRET`을 새 값으로 바꾼다. `SPRING_JPA_HIBERNATE_DDL_AUTO`가 없는지도 확인한다.
2. EC2에서 애플리케이션을 멈춘다 (`sudo docker stop spring-app`).
3. 접속한 호스트와 DB 이름을 확인하고 RDS 스키마를 비운다.
4. Redis를 비운다 (`sudo docker exec redis redis-cli FLUSHDB`).
5. PR을 머지한다. 배포 로그에서 V1 적용을 확인한다.
6. 바로 약관을 넣는다. 시연 데이터가 필요하면 그 뒤에 시드를 켜고 다시 배포한다.

### 시연용 가상 데이터

과외 · 참여자 생성 API가 없어 시연 데이터를 부팅 때 만든다 (`global/seed`). 정산 서비스를 거치지 않고 엔티티를 직접 만들어 한 트랜잭션으로 저장한다(중간 실패 시 아무것도 남지 않음). 정산 리포트만은 실제 리포트 생성기(`SettlementReportCreator`)로 만들어 통계 · 진도 표가 정산과 같은 계산으로 나온다. 날짜는 모두 `Clock` 빈(Asia/Seoul) 기준 실행일의 상대값이다.

**켜는 법** — `.env`(또는 환경 변수)에 넣고 부팅한다. 프로파일이 아니라 이 값 하나로 켜고 끈다.

| 키 | 기본값 | 설명 |
| --- | --- | --- |
| `SUDAM_SEED_ENABLED` | `false` | `true`일 때만 실행기 빈이 생긴다 (`sudam.seed.enabled`) |
| `SUDAM_SEED_TEACHER_KAKAO_ID` | 빈 값 | 시드 선생님의 카카오 회원번호. 비우면 가짜 계정 `seed-teacher-1` |
| `SUDAM_SEED_RESET` | `false` | `SUDAM_SEED_ENABLED`와 함께 `true`면 재기동 때 시드 선생님의 과외를 지우고 처음 상태로 다시 만든다 (`sudam.seed.reset`) |

- 회원번호가 기본값이면 부팅 로그에 액세스 토큰을 한 번 출력한다 (30분 뒤 만료, 다시 받으려면 재부팅). 파일 · DB · 응답에는 남기지 않는다.
- 회원번호를 넣으면 실제 사람 계정이므로 토큰을 출력하지 않는다. 그 카카오 계정으로 한 번 로그인해 둔 회원이 있으면 그 회원에 데이터를 붙이고, 없으면 새로 만든다. 시연 때 카카오 로그인으로 들어간다.

**반복 실행** — 시드 선생님(KAKAO, 회원번호)에게 과외가 하나라도 있으면 아무것도 만들지 않고 `가상 데이터가 이미 있어 건너뜀` 로그만 남긴다. 데이터가 부팅 사이에 유지되므로 처음 한 번만 만들어진다.

**리셋 (UT 리허설 뒤 되돌리기)** — 리허설로 바뀐 데이터를 처음 상태로 돌린다.

1. `SUDAM_SEED_RESET=true`로 재기동한다. `DemoSeedResetter`가 시드 선생님의 과외와 그 아래 데이터(일정 · 교재 · 정책 · 설정 · 참여자 · 사이클 · 회차 · 기록 · 진도 · 리포트 · 청구 · 입금 · 교육비 조정)를 FK 역순으로 지우고, 같은 트랜잭션에서 시드를 다시 만든다. 다시 만들다 실패하면 삭제도 롤백된다.
2. User · TeacherProfile · 약관 동의는 지우지 않는다. 카카오로 로그인한 상태가 그대로 유지된다. 회원번호를 넣은 실제 계정이면 그 계정에서 손으로 만든 과외도 함께 지워진다.
3. 끝나면 `SUDAM_SEED_RESET`을 끄고 다시 재기동한다. 켜 둔 채면 재기동할 때마다 지워진다 (부팅 로그에 경고가 크게 찍힌다).

테이블이 늘면 `DemoSeedResetter.STATEMENTS`에 FK 순서대로 추가한다 (예: 리포트 열람 테이블은 `SettlementReport` 삭제 앞).

**데이터**

- 선생님 한서윤: 프로필(영어) + 현재 시행 중인 필수 약관 동의 → `GET /users/me`의 status = `ACTIVE`.
- 과외마다 공통: 영어 과외, 수업 일정, 교재 1권, 선불 정책(사이클 1부터, 국민은행 · 가짜 계좌, 예금주 한서윤), 공유 설정, 학부모(어머님, 가짜 번호 `010000000xx`, 링크 토큰 발급).
- 수입 화면(T-15) · 리포트(T-13 · T-14)용 4개 — 사이클 1(실행 5주 전 시작, 1주 전 SETTLED) · 사이클 2 IN_PROGRESS, 사이클 2를 청구하는 선불 '다음 N회차' 청구.
  - 사이클 1에 정산 회차만큼 회차 · 기록 · 진도(회차마다 2단원)를 깔고, 실제 정산 순서(정산 → 다음 사이클 → 리포트 → 청구)로 리포트를 만들어 청구 `report_id`에 연결한다. 실제 정산은 사이클을 닫을 때 항상 리포트를 만들므로 4명 모두 리포트가 있다. 이해도 분포는 학생마다 다르게 두었다.
  - 리포트마다 그 학생 기록을 바탕으로 쓴 AI 초안 코멘트를 넣는다. UT에서 AI 생성을 빼서 시드가 대신 넣는다.
  - 리포트 상태는 UT 설계서 시작 상태(#47)다. 박지후만 확인 전 `GENERATED`(T1 · T2), 나머지는 선생님이 초안을 그대로 확인해 정산 1시간 뒤 보낸 `SENT`. 윤서아 · 김하린은 보낸 3시간 뒤 학부모 첫 열람(`settlement_report_views`)이 있고, 이도윤은 안 읽음(T3). 김하린은 설계서에 없어 '생성됨'을 박지후 한 장으로 두려고 보낸 상태로 뒀다.

| 학생 | 정산 회차 · 수업료 | 청구 상태 | 카드 아래 줄 | 사이클 1 리포트 (이해도 상/중/하, 숙제율 · 상태) |
| --- | --- | --- | --- | --- |
| 박지후 | 8 · 360,000 | CONFIRM_REQUESTED (실행 2시간 전 학부모 요청) | `PARENT_CONFIRMED` | 있음 (6/2/0, 93% · 생성됨) |
| 윤서아 | 8 · 360,000 | PENDING, 미입금 알림 켬 | `REMINDER_SCHEDULED` | 있음 (2/5/1, 71% · 보냄 · 열람) |
| 이도윤 | 4 · 200,000 | PARTIAL (실행 하루 전 120,000 입금) | `PARTIAL_PAID` | 있음 (0/1/3, 50% · 보냄 · 안 읽음) |
| 김하린 | 8 · 280,000 | PAID (실행 하루 전 전액 입금) | `NONE` | 있음 (5/3/0, 93% · 보냄 · 열람) |

- 시연용 김서아(T-09 · T-14) — 고1 · 선불 · 8회 360,000원(회당 45,000). 수업은 18:00~19:30, 실행일이 월 · 수 · 금이면 월 · 수 · 금, 아니면 실행일 요일과 그 뒤 2일 · 4일 요일(오늘이 항상 수업일). 사이클 1 IN_PROGRESS에 오늘 **전** 수업일 7회의 기록 · 진도(수능특강 영어, Unit 1~14). 오늘 회차 · 청구(선불 첫 청구 포함)는 없다.
- 안내일(`notified_at`)은 비어 있다. 목록은 `created_at`(실행 시각)으로 이번 달에 들어온다.

**시연 순서**

1. 부팅 로그의 토큰을 복사하거나(기본 계정) 카카오로 로그인한 뒤 Swagger(`/swagger-ui.html`) **Authorize**에 넣는다. 부팅 로그에 김서아 `tutoringId` · 오늘 `scheduleId` · `originalDate`가 함께 찍힌다.
2. `GET /api/v1/invoices` → 요약 1,200,000 / 400,000 / 800,000, 카드 4장(박지후 → 윤서아 → 이도윤 → 김하린).
   - 각 청구의 `reportId`로 `GET /api/v1/reports/{reportId}`와 `GET /api/v1/reports/{reportId}/invoice`를 열 수 있다.
   - 코멘트 · 발송 (박지후): `GET .../comment`(AI 초안, 확인 전) → `POST .../send`는 `REPORT409_2` → `PUT .../comment`(`text`) → `POST /api/v1/shares`(`SETTLEMENT_REPORT`, 리포트 ID, 리포트 조회 `parents`의 학부모 참여자 ID → 카톡 메시지 · `url`) → `POST .../send` → `SENT` (공유 기록도 `SENT`).
   - 학부모 링크: `/shares` 응답 `url`의 토큰으로 `GET /api/v1/links/{token}/reports/{reportId}`(로그인 없음) → 목록의 `viewedAt`이 채워진다.
   - 입금 안내 다시 보내기 (윤서아): `POST /api/v1/shares`(`PAYMENT_RESEND`, 청구 ID, 카드 `parents`의 학부모 ID) → `POST /api/v1/invoices/{invoiceId}/renotifications` → 카드 아래 줄 `RESENT`.
3. `POST /api/v1/tutorings/{김서아 tutoringId}/records` (`scheduleId` = 오늘 요일 일정, `originalDate` = 오늘, 교재 진도 15~16) → 응답 `noInCycle: 8`, `settlement.settled = true`, `reportId` · `invoiceId`.
4. `GET /api/v1/reports/{reportId}` → 김서아 8/8 리포트. 실제 정산으로 만든 리포트라 코멘트가 없다(`comment: null`). 보내려면 먼저 `PUT .../comment`로 작성한다.
5. `GET /api/v1/invoices` → 1,560,000 / 400,000 / 1,160,000, 카드 5장(김서아 '다음 8회차' 360,000이 입금 대기로 추가).

**배포에서 쓸 때 주의** — 시연이 끝나면 `SUDAM_SEED_ENABLED`를 끈다. 옛 시드(리포트 없음 · 코멘트 없음)가 이미 있으면 한 번 `SUDAM_SEED_RESET=true`로 재기동해야 리포트 · AI 초안이 생긴다. 리허설에서 보낸(SENT) 박지후 리포트 · 열람 기록 · 다시 보낸 입금 안내도 리셋해야 처음 상태로 돌아온다. 기본 계정의 토큰이 서버 로그에 남으므로 로그 접근 범위를 확인한다.

## 아직 정해지지 않은 것

- 다중 기기 로그인 — `rt:{userId}`가 회원당 1개라 두 번째 로그인이 첫 세션을 끊는다.
- 탈퇴 보강 — 카카오 unlink, 재가입 제한, 타 도메인 연락처 정리.
- 운영 프로파일 — `application-prod.yaml`. 개발 서버는 `application-dev.yaml`(`ddl-auto: validate` 고정, 쿠키 `Secure`, 도메인 기준 리다이렉트 · CORS 출처)을 쓴다.
- ERD의 생성 컬럼 + 유일 제약 — Flyway로 추가할 수 있지만 아직 만들지 않았다. 별도 이슈에서 추가하기 전까지 서비스에서 보장한다.
- 과외 · 참여자 · 교육비 API — 엔티티만 있고 컨트롤러 · 서비스는 아직 없다. 정산은 수업 기록 저장이 같은 트랜잭션에서 실행한다 (`TutoringRecordService` → `SettlementService.settleIfReached`).
