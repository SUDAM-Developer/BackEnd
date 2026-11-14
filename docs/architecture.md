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
│   │   ├── enums                       # Role, Provider, Subject, TermsCode, UserStatus
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
│   ├── cycle                           # 수업 사이클 = 정산 단위 묶음 (엔티티 · 레포지토리만)
│   │   ├── entity/Cycle.java           # open / settle / isInProgress
│   │   ├── enums/CycleStatus.java      # IN_PROGRESS, SETTLED
│   │   ├── repository                  # CycleRepository: 진행 중 · 번호별 · 과외별 목록 조회
│   │   └── exception                   # CycleException, code/CycleErrorCode (CYCLE409_1)
│   └── invoice                         # 교육비 청구 · 입금 · 금액 수정 이력 (엔티티 · 레포지토리만)
│       ├── entity                      # Invoice, InvoicePayment, InvoiceAmountAdjustment
│       │   └── InvoiceSnapshot.java    # 계산 근거 JSON 값 객체 (CanceledLesson, RecordedPayment 포함)
│       ├── enums                       # InvoiceKind, InvoiceStatus, AdjustedBy
│       ├── repository                  # InvoiceRepository는 잠금 조회 findByIdForUpdate 제공
│       └── exception                   # InvoiceException, code/InvoiceErrorCode
└── global                              # 공통 관심사
    ├── ApiResponse.java                # 모든 응답의 래퍼
    ├── exception                       # ProjectException, code/Base*Code · General*Code
    ├── handler/GeneralExceptionHandler # @RestControllerAdvice
    ├── entity/BaseTimeEntity.java      # createdAt / updatedAt 감사
    ├── crypto/AccountNumberConverter   # 계좌번호 AES-256-GCM JPA 변환기
    ├── config                          # SecurityConfig, JpaConfig, SwaggerConfig
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
| INVOICE | — | 404_1, 409_1 ~ 409_5 |

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

과목은 `Subject` enum이고 `teacher_profiles.subjects`는 enum 이름 배열 JSON 컬럼이다. `OTHER`일 때만 `subject_other`를 쓴다.

## 설정 / 실행 전제

- `application.yaml`이 `optional:file:.env[.properties]`를 import한다. 키: `DB_URL` `DB_USERNAME` `DB_PASSWORD` `KAKAO_CLIENT_ID` `KAKAO_CLIENT_SECRET` `JWT_SECRET` `ACCOUNT_ENCRYPTION_KEY`(Base64 32바이트, 없거나 길이가 틀리면 기동 실패). Redis 호스트 · 포트와 리다이렉트 URI는 `application-local.yaml`.
- MySQL과 Redis가 모두 떠 있어야 한다. Redis가 없으면 부팅은 되지만 로그인과 인증 요청이 깨진다.
- JPA: `ddl-auto: create`(부팅마다 스키마 재생성), `open-in-view: false`, `default_batch_fetch_size: 100`.
- 시드: `spring.sql.init.mode: always`로 스키마 생성 뒤 `data.sql`이 매번 실행된다. 현재는 약관 3행(SERVICE · PRIVACY · CONTACT, 모두 필수). 약관은 코드로 만들지 않으므로 새 버전은 이 파일에 행을 추가한다.
- Swagger: `/swagger-ui.html`, `/v3/api-docs`. `@Tag` · `@Operation` 요약만 담고 에러 코드별 응답은 `docs/api-spec.md`가 기준이다.
- 토큰 만료: AT 30분, RT 14일 (`application.yaml`의 `jwt.*`).

## 아직 정해지지 않은 것

- 다중 기기 로그인 — `rt:{userId}`가 회원당 1개라 두 번째 로그인이 첫 세션을 끊는다.
- 탈퇴 보강 — 카카오 unlink, 재가입 제한, 타 도메인 연락처 정리.
- 운영 프로파일 — `application-prod.yaml`, `ddl-auto` 전략, 마이그레이션 도구, 쿠키 `secure` / `SameSite=None` 전환.
- CORS — `SecurityConfig`에 설정 없음.
- ERD의 생성 컬럼 + 유일 제약 — `ddl-auto`로 만들 수 없어 마이그레이션 도구 도입 전까지 서비스에서 보장한다.
- 과외 · 참여자 · 교육비 API — 엔티티만 있고 컨트롤러 · 서비스는 아직 없다.
