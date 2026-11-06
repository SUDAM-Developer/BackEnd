# 아키텍처

> 최종 수정: 2026-11-05
> 전체 구조와 요청 처리 흐름. 세부 규칙은 `.claude/rules/`의 각 파일에 있다.

## 기술 스택

| 항목 | 버전 / 내용 |
| --- | --- |
| 언어 | Java 21 (Gradle toolchain) |
| 프레임워크 | Spring Boot 4.1.1 |
| 빌드 | Gradle 9.8.1 (wrapper) |
| DB | MySQL 8 (`mysql-connector-j`), Spring Data JPA / Hibernate |
| 보안 | Spring Security + OAuth2 Client (카카오) |
| 검증 | Bean Validation (`spring-boot-starter-validation`) |
| 보일러플레이트 | Lombok (main / test 양쪽) |

Boot 4라서 스타터 이름이 Boot 3와 다르다 (`spring-boot-starter-webmvc`, `...-webmvc-test`, `...-data-jpa-test`). 자세한 내용은 `CLAUDE.md` 참고.

## 패키지 구조

```
com.sudam
├── SudamApplication.java
├── domain                          # 비즈니스 도메인별 수직 분할
│   └── user
│       └── enums/Role.java         # USER, ADMIN
└── global                          # 도메인 공통 관심사
    ├── ApiResponse.java            # 모든 응답의 공통 래퍼
    ├── code
    │   ├── BaseSuccessCode.java    # status / code / message 인터페이스
    │   ├── BaseErrorCode.java
    │   ├── GeneralSuccessCode.java # COMMON200, COMMON201
    │   └── GeneralErrorCode.java   # COMMON400 ~ COMMON503
    ├── exception
    │   └── ProjectException.java   # BaseErrorCode를 품은 런타임 예외
    ├── handler
    │   └── GeneralExceptionHandler.java  # @RestControllerAdvice
    └── security
        ├── SecurityConfig.java
        ├── exception               # AuthErrorCode, AuthException
        └── oauth2
            ├── CustomOAuth2User.java        # principal (userId + Role)
            ├── CustomOAuth2UserService.java # attributes → OAuth2Response → principal
            ├── response                     # OAuth2Response, KakaoResponse
            └── handler                      # 로그인 성공 / 실패 핸들러
```

도메인이 늘어나면 `domain.<도메인>` 아래에 `controller` / `service` / `repository` / `entity` / `dto` / `exception`을 둔다 (`.claude/rules/code-convention.md`).

## 요청 처리 흐름

### 일반 API

```
Client
  → SecurityFilterChain (인증 확인, STATELESS)
  → Controller      요청 바인딩 + @Valid 검증, 응답 조립만
  → Service         @Transactional 경계, 비즈니스 규칙, 실패 시 ProjectException
  → Repository      JpaRepository
  → Service         엔티티 → 응답 DTO 변환 (open-in-view: false이므로 여기서 끝내야 한다)
  → Controller      ResponseEntity<ApiResponse<T>>
```

예외는 어디서 던져도 `GeneralExceptionHandler`가 받아 `ApiResponse.onFailure(...)` 하나의 포맷으로 변환한다. 컨트롤러·서비스에서 에러 `ResponseEntity`를 직접 만들지 않는다.

### 소셜 로그인

```
GET /oauth2/authorization/kakao
  → 카카오 인가 페이지
  → GET /login/oauth2/code/kakao (code)
  → CustomOAuth2UserService.loadUser
        DefaultOAuth2UserService로 user-info 조회
        registrationId 분기 → KakaoResponse (provider / providerId / email / name)
        필수 동의 항목 누락 시 AuthException(AUTH401_1)
        → CustomOAuth2User(userId, Role)
  → 성공: OAuth2LoginSuccessHandler   (현재 로그만, JWT 발급 TODO)
  → 실패: OAuth2LoginFailureHandler   (현재 로그만, 리다이렉트 TODO)
```

인증 필터 단계의 예외는 `@RestControllerAdvice`에 도달하지 않는다. `OAuth2LoginFailureHandler`가 직접 처리한다.

## 설정 / 프로파일

- `application.yaml` — 공통 설정. `optional:file:.env[.properties]`를 import해 비밀값을 환경 변수로 받는다.
- `application-local.yaml` — 로컬 전용 (카카오 redirect-uri). 기본 활성 프로파일은 `local`.
- 필요한 `.env` 키: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` (`.env.example` 참고).
- JPA: `ddl-auto: create`(부팅마다 스키마 재생성), `open-in-view: false`, `default_batch_fetch_size: 100`, `format_sql: true`.

## 아직 정해지지 않은 것

- **JWT 인증** — 토큰 발급·검증 필터·재발급·로그아웃이 전부 미구현. 현재 로그인 성공 후 클라이언트가 받을 자격증명이 없다.
- **회원 영속화** — `User` 엔티티와 레포지토리가 없어 `CustomOAuth2UserService`가 `userId = 1L`을 하드코딩한다.
- **운영 프로파일** — `application-prod.yaml`, `ddl-auto` 운영 전략, 마이그레이션 도구(Flyway 등) 미도입.
- **CORS** — `SecurityConfig`에 설정이 없다. 프론트 연동 시 추가 필요.
- **API 문서 자동화** — Swagger/OpenAPI 미도입. 현재는 `docs/api-spec.md` 수동 관리.