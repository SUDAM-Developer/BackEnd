# CLAUDE.md

Claude Code가 이 저장소에서 **항상** 지켜야 하는 핵심 규칙만 담는다.
영역별 상세 규칙은 `.claude/rules/`, 분량이 크고 때때로 필요한 참고 자료는 `docs/`에 있다.

## 프로젝트

"sudam" 백엔드 API 서버. Spring Boot 4.1.1 / Java 21 / Gradle 9.8.1 / MySQL 8 + JPA(Hibernate) / Redis(토큰 저장소) / Spring Security OAuth2 Client(카카오 소셜 로그인) + JWT.

- 기본 패키지는 `com.sudam`. Gradle `group`은 `com`이므로 group에서 패키지를 추론하지 말 것.
- 패키지는 2축 구조: `com.sudam.domain.<도메인>` (비즈니스), `com.sudam.global.<관심사>` (공통 응답·예외·보안·설정).

## 명령어

래퍼를 사용한다 (Windows는 `gradlew.bat`, 그 외는 `./gradlew`).

```powershell
.\gradlew.bat build                                  # 컴파일 + 테스트
.\gradlew.bat bootRun                                # 애플리케이션 실행
.\gradlew.bat test                                   # 전체 테스트
.\gradlew.bat test --tests "com.sudam.SudamApplicationTests"               # 클래스 단위
.\gradlew.bat test --tests "com.sudam.SudamApplicationTests.contextLoads"  # 메서드 단위
```

테스트 HTML 리포트: `build/reports/tests/test/index.html`. 린터/포매터는 설정되어 있지 않다.

## 실행 전제 1: MySQL과 **Redis**가 모두 떠 있어야 한다

리프레시 토큰(`rt:{userId}`)과 액세스 토큰 차단 목록(`bl:{jti}`)을 Redis에 둔다. Redis가 없으면 **부팅은 되지만** 소셜 로그인 성공 처리와 인증이 필요한 모든 요청이 `RedisConnectionFailureException`으로 깨진다 (`StringRedisTemplate`이 지연 연결이라 기동 시점에는 드러나지 않는다).

```powershell
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

접속 정보는 프로파일별 파일에 있다 (`application-local.yaml`의 `spring.data.redis.host` / `port`). `.env` 키가 아니다 — 배포 프로파일을 만들 때 그 파일에 호스트를 적는다.

## 실행 전제 2: `.env`가 반드시 있어야 한다

`application.yaml`이 `optional:file:.env[.properties]`를 import하고 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` / `JWT_SECRET` / `ACCOUNT_ENCRYPTION_KEY`를 참조한다. `.env`는 git-ignore 대상이고 필요한 키는 `.env.example`에 있다.

- `JWT_SECRET`: 토큰 서명 키. 문자열을 UTF-8 바이트 그대로 HMAC 키로 쓰므로 **32바이트 이상**이어야 한다 (예: `openssl rand -base64 48`). 짧으면 `JwtUtil` 빈 생성에서 기동이 실패한다.
- `ACCOUNT_ENCRYPTION_KEY`: 계좌번호 AES-GCM 키. **Base64로 인코딩한 정확히 32바이트** (`openssl rand -base64 32`). 형식이 틀리면 기동이 실패하고, 키를 바꾸면 이미 저장된 계좌번호를 복호화할 수 없다.

- import가 `optional:`이라 `.env`가 없으면 친절한 메시지 없이 `${DB_URL}` 미해석으로 `DataSourceProperties$DataSourceBeanCreationException`이 터진다. 이 예외를 만나면 코드 문제가 아니라 `.env` 누락/불완전이 원인이다.
- `SudamApplicationTests`는 전체 `@SpringBootTest`라 **테스트에도 실제 MySQL 연결이 필요**하다. H2 폴백이나 Testcontainers는 없다. 그래서 기본 선택은 컨텍스트를 띄우지 않는 단위 테스트다 (`.claude/rules/test-convention.md`).
- `ddl-auto: create`이므로 부팅마다 대상 DB 스키마가 drop/recreate된다. `DB_URL`을 보존해야 하는 DB로 절대 가리키지 말 것.

## Spring Boot 4 — 스타터 이름이 Boot 3와 다르다

Boot 4에서 스타터가 개편되어, 예제에 흔히 나오는 Boot 3 이름은 여기서 틀린 이름이다.

| 쓰지 말 것 (Boot 3) | 쓸 것 (Boot 4, `build.gradle` 기준) |
| --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` | `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test` |

테스트 지원도 모듈별로 쪼개져 있다. 새 테스트가 필요한 슬라이스의 모듈이 `testImplementation`에 없으면, `spring-boot-starter-test`를 끌어오지 말고 해당 모듈의 `-test` 스타터를 추가한다.

## 항상 지킬 짧은 규칙

- 작업 브랜치는 `dev`에서 분기하고(`<라벨>/<이슈번호>-<브랜치 내용>`, 예: `feat/12-kakao-social-login`) PR도 `dev`로 올린다. `main`은 릴리스 전용.
- PR 제목은 라벨 이모지 + 태그 + 요약 + 이슈 번호 형식: `✨[FEAT] 한국어 요약 (#12)`. 커밋 메시지는 대괄호 없는 소문자 태그 + 이슈 번호 형식: `feat: 회원가입 API 추가 (#12)`. 요청받지 않으면 commit / push 하지 않는다. 라벨별 이모지 기준표는 `.claude/rules/github-convention.md`.
- 주석·커밋·PR·이슈·문서는 **한국어**로 쓴다. 코드 식별자는 영어.
- 설정은 `application.yaml`에 둔다 (`.properties` 아님). 프로파일별 설정은 `application-<profile>.yaml`.
- Lombok은 main/test 소스셋 모두에 설정돼 있다 (`compileOnly` + `annotationProcessor`).
- `.env`는 커밋하지 않는다. 키를 추가하면 `.env.example`에도 빈 값으로 함께 추가한다.
- `HELP.md`는 git-ignore된 Initializr 생성물이다. 수정도 참조도 하지 않는다.
- 응답 본문은 항상 `ApiResponse`로 감싼다. 컨트롤러는 `ApiResponse<T>`를 그대로 반환하고 `ResponseEntity`로 감싸는 것은 201 Created뿐이다. 성공 코드는 `GeneralSuccessCode`가 아니라 도메인별 `<도메인>SuccessCode`를 쓴다. 상세는 `.claude/rules/api-convention.md`.
- 성공/에러 코드 enum은 `<스코프>/exception/code/`에, 예외 클래스는 그 한 단계 위 `exception/`에 둔다. 예외는 `ProjectException`을 상속한 도메인 예외로 던지고 컨트롤러에서 try-catch 하지 않는다. 상세는 `.claude/rules/error-code-convention.md`.

## 영역별 상세 규칙 — `.claude/rules/`

각 파일은 frontmatter의 `paths:`에 해당하는 파일을 다룰 때 적용된다. 그 영역을 건드릴 때 해당 파일을 읽는다.

| 파일 | 다루는 내용 |
| --- | --- |
| `code-convention.md` | Java/Spring 코드 컨벤션 — 레이어 구조, 의존성 주입, Lombok 사용 범위, 네이밍 |
| `api-convention.md` | API 작성 규칙 — 컨트롤러/DTO, `ApiResponse` 사용, URL·HTTP 메서드, 요청 검증 |
| `error-code-convention.md` | 성공/에러 코드와 예외 설계 — 코드 문자열 규칙, `BaseErrorCode` 구현, 핸들러 확장 |
| `jpa-convention.md` | 엔티티·레포지토리 규칙 — 연관관계, 지연 로딩, N+1, 트랜잭션 경계 |
| `security-convention.md` | 인증/인가 — OAuth2 로그인 체인, principal, 허용 URI 추가 방법 |
| `test-convention.md` | 테스트 작성법 — 슬라이스 선택, 네이밍, DB 의존 테스트 주의점 |
| `github-convention.md` | PR / 이슈 작성 컨벤션 (제목 태그, 템플릿 섹션). **PR이나 이슈를 작성할 때 먼저 읽는다.** |

## 참고 자료 — `docs/`

분량이 크고 자주 필요하지 않은 문서. 필요할 때만 읽는다.

| 파일 | 언제 읽는가 |
| --- | --- |
| `docs/erd.md` | 엔티티를 추가/수정하거나 테이블 관계를 확인해야 할 때 |
| `docs/api-spec.md` | API를 추가/변경하거나 기존 엔드포인트의 요청·응답 스펙을 확인할 때 |
| `docs/architecture.md` | 패키지 구조·요청 처리 흐름·공통 모듈의 전체 그림이 필요할 때 |

코드를 바꿀 때 해당 문서가 낡으면 함께 갱신한다.