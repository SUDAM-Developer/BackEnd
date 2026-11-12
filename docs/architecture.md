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
│   ├── auth                        # 인증 도메인 (토큰 재발급·로그아웃)
│   │   ├── controller/AuthController.java
│   │   ├── service/AuthService.java
│   │   ├── dto
│   │   │   ├── TokenPair.java            # 서비스 반환형 (AT는 본문, RT는 쿠키)
│   │   │   └── response/TokenReissueResponse.java
│   │   └── exception
│   │       ├── AuthException.java        # extends ProjectException
│   │       └── code
│   │           ├── AuthErrorCode.java    # AUTH400_1, AUTH401_1 ~ AUTH401_6
│   │           └── AuthSuccessCode.java  # AUTH200_1
│   └── user
│       ├── entity/User.java
│       ├── enums                   # Role(TEACHER, PARENT, STUDENT), Provider(KAKAO)
│       ├── repository/UserRepository.java
│       └── service/UserService.java
└── global                          # 도메인 공통 관심사
    ├── ApiResponse.java            # 모든 응답의 공통 래퍼
    ├── exception
    │   ├── ProjectException.java   # BaseErrorCode를 품은 런타임 예외
    │   └── code
    │       ├── BaseErrorCode.java      # status / code / message 인터페이스
    │       ├── BaseSuccessCode.java
    │       ├── GeneralErrorCode.java   # COMMON400 ~ COMMON503
    │       └── GeneralSuccessCode.java # COMMON200, COMMON201
    ├── handler
    │   └── GeneralExceptionHandler.java  # @RestControllerAdvice
    ├── entity/BaseTimeEntity.java  # createdAt / updatedAt 감사
    ├── config                      # SecurityConfig, JpaConfig, SwaggerConfig
    └── security                    # 인증 인프라 (도메인 코드는 domain/auth에 있다)
        ├── filter/JwtAuthenticationFilter.java
        ├── token/TokenStore.java           # Redis — rt:{userId}, bl:{jti}
        ├── util                            # JwtUtil, CookieUtil
        ├── exception                       # EntryPoint(401), AccessDeniedHandler(403)
        └── oauth2
            ├── CustomOAuth2User.java        # principal (userId + Role)
            ├── CustomOAuth2UserService.java # attributes → OAuth2Response → principal
            ├── response                     # OAuth2Response, KakaoResponse
            ├── exception                    # SocialLoginException
            └── handler                      # 로그인 성공 / 실패 핸들러
```

**성공/에러 코드 enum은 항상 `<스코프>/exception/code/`에 둔다.** 예외 클래스는 그 한 단계 위 `exception/`이다. 코드 enum은 도메인 자산이므로 인증 코드도 `global/security`가 아니라 `domain/auth/exception/code/`에 있고, `global/security`에는 필터·토큰 저장소·유틸·OAuth2 배선만 남는다.

도메인이 늘어나면 `domain.<도메인>` 아래에 `controller` / `service` / `repository` / `entity` / `dto` / `enums` / `exception`(+`exception/code`)을 둔다 (`.claude/rules/code-convention.md`).

## 요청 처리 흐름

### 일반 API

```
Client
  → SecurityFilterChain (인증 확인, STATELESS)
  → Controller      요청 바인딩 + @Valid 검증, 응답 조립만
  → Service         @Transactional 경계, 비즈니스 규칙, 실패 시 ProjectException
  → Repository      JpaRepository
  → Service         엔티티 → 응답 DTO 변환 (open-in-view: false이므로 여기서 끝내야 한다)
  → Controller      ApiResponse<T> (201 Created만 ResponseEntity로 감싼다)
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
  → 성공: OAuth2LoginSuccessHandler
        AT·RT 발급 → TokenStore.saveRefreshToken(rt:{userId}, 14일)
        302 {프론트}#accessToken={AT} + Set-Cookie: refreshToken (HttpOnly)
  → 실패: OAuth2LoginFailureHandler
        302 {프론트}#code={에러 코드}
```

인증 필터 단계의 예외는 `@RestControllerAdvice`에 도달하지 않는다. `OAuth2LoginFailureHandler`가 직접 처리한다.

### 인증이 필요한 요청

```
Authorization: Bearer {AT}
  → JwtAuthenticationFilter
        JwtUtil.parseAccessToken   서명·만료·typ=access  실패 → AUTH401_3
        TokenStore.isBlacklisted(jti)  차단됨 → AUTH401_4
        → SecurityContext에 CustomOAuth2User(userId, Role)
  → (실패 시) 사유만 request attribute에 담고 체인 통과
       → AuthorizationFilter가 거부 → CustomAuthenticationEntryPoint
       → handlerExceptionResolver → ApiResponse.onFailure
```

### 토큰 재발급 (RTR)

```
POST /api/v1/auth/reissue   (permitAll, Cookie: refreshToken)
  → JwtUtil.parseRefreshToken        실패 → AUTH401_3
  → TokenStore.findRefreshToken(userId)
        없음        → AUTH401_5
        값 불일치    → rt:{userId} 삭제 후 AUTH401_6  (탈취 의심)
        일치        → 새 AT·RT 발급 → rt:{userId} 덮어쓰기
  → ApiResponse(AUTH200_1) { accessToken } + Set-Cookie: refreshToken
```

한 번 쓴 RT를 다시 보내면 그 회원의 세션이 모두 끊긴다. AT와 RT 모두 `jti`를 가져 발급마다 값이 달라진다.

### 로그아웃 / 회원 탈퇴

```
POST /api/v1/auth/logout        AuthService.logout(bearer)
DELETE /api/v1/users/me         UserService.withdraw(userId) + AuthService.logout(bearer)

AuthService.logout
  → rt:{userId} 삭제
  → bl:{jti} 등록 (TTL = AT 남은 만료 시간, 최대 30분)
  → Set-Cookie: refreshToken=; Max-Age=0

UserService.withdraw
  → users.withdrawn_at = now(), email = NULL, provider_id = NULL
  → provider_id가 NULL이라 같은 소셜 계정으로 재로그인하면 새 회원으로 가입
```

세션이 STATELESS라 발급된 AT는 서버가 따로 막지 않으면 만료까지 유효하다. 그래서 차단 목록이 필요하다.

## 설정 / 프로파일

- `application.yaml` — 공통 설정. `optional:file:.env[.properties]`를 import해 비밀값을 환경 변수로 받는다.
- `application-local.yaml` — 로컬 전용 (카카오 redirect-uri, 프론트 리다이렉트 URI, Redis 호스트·포트). 기본 활성 프로파일은 `local`.
- 필요한 `.env` 키: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `JWT_SECRET` (`.env.example` 참고). Redis 접속 정보는 `.env`가 아니라 프로파일 파일에 둔다.
- **MySQL과 Redis가 모두 떠 있어야 한다.** Redis가 없으면 부팅은 되지만 로그인 성공 처리와 인증 요청이 깨진다 (`CLAUDE.md` 실행 전제).
- JPA: `ddl-auto: create`(부팅마다 스키마 재생성), `open-in-view: false`, `default_batch_fetch_size: 100`, `format_sql: true`.
- Swagger(OpenAPI): `SwaggerConfig`가 제목(`SUDAM API 명세`)과 JWT 보안 스키마(`bearerAuth`)만 정의한다. UI는 `/swagger-ui.html`, 문서는 `/v3/api-docs`이며 두 경로 모두 `SecurityConfig.allowUris`로 공개돼 있다. Authorize 창에는 `Bearer ` 없이 액세스 토큰만 넣는다.

## 아직 정해지지 않은 것

- **다중 기기 로그인** — `rt:{userId}`가 회원당 1개라 두 번째 로그인이 첫 세션을 무효화한다. 기기별로 두려면 키 구조를 바꿔야 한다.
- **탈퇴 보강** — 카카오 연결 끊기(unlink, ERD 1.2.3), 재가입 제한 정책, 연락처가 담긴 타 도메인 데이터 정리가 빠져 있다.
- **운영 프로파일** — `application-prod.yaml`, `ddl-auto` 운영 전략, 마이그레이션 도구(Flyway 등) 미도입. 배포 시 `CookieUtil`의 `secure(true)` / `SameSite=None` 전환도 함께 필요하다.
- **CORS** — `SecurityConfig`에 설정이 없다. 프론트 연동 시 추가 필요.
- **API 명세 문서의 역할 분담** — Swagger는 `@Tag`·`@Operation` 수준의 요약만 담는다. 에러 코드별 응답 예시는 여전히 `docs/api-spec.md` 수동 관리다.