---
description: Java/Spring 코드 컨벤션 — 패키지·레이어 구조, 의존성 주입, Lombok 사용 범위, 네이밍
paths:
  - "src/main/java/**/*.java"
---

# 코드 컨벤션

## 패키지 구조

```
com.sudam
├── domain
│   └── <도메인>            # user, post, ...
│       ├── controller
│       ├── service
│       ├── repository
│       ├── entity
│       ├── dto
│       │   ├── request
│       │   └── response
│       ├── enums
│       └── exception       # 도메인 전용 에러 코드 / 예외
└── global
    ├── code                # BaseSuccessCode / BaseErrorCode / General*
    ├── exception           # ProjectException 등 공통 예외
    ├── handler             # @RestControllerAdvice
    ├── security            # SecurityConfig, oauth2, 인증 예외
    └── config              # 그 외 설정 클래스
```

- 새 기능은 먼저 **어느 도메인인지** 정하고 `domain.<도메인>` 아래에 레이어별로 배치한다. 공통으로 두 도메인 이상이 쓰는 것만 `global`로 올린다.
- 레이어를 건너뛰지 않는다: `controller → service → repository`. 컨트롤러에서 레포지토리를 직접 호출하지 않는다.
- 엔티티를 컨트롤러 밖으로 노출하지 않는다. 경계에서는 항상 DTO를 쓴다.

## 의존성 주입

- **생성자 주입만** 사용한다. `@Autowired` 필드 주입과 세터 주입은 쓰지 않는다.
- 클래스에 `@RequiredArgsConstructor`를 붙이고 의존성은 `private final`로 선언한다 (기존 `SecurityConfig`, `CustomOAuth2UserService`와 동일).

## Lombok

- 쓰는 것: `@Getter`, `@RequiredArgsConstructor`, `@Builder`, `@Slf4j`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.
- 쓰지 않는 것: `@Setter`(엔티티에 특히 금지 — 의미 있는 메서드로 상태를 바꾼다), `@Data`, `@AllArgsConstructor`(public), `@EqualsAndHashCode`(엔티티).
- 로깅은 `@Slf4j` + `log.xxx("... {}", arg)` 플레이스홀더 방식. 문자열 연결로 로그를 만들지 않는다.

## 네이밍

- 클래스: `XxxController`, `XxxService`, `XxxRepository`, `XxxRequest`, `XxxResponse`, `XxxErrorCode`, `XxxException`.
- 상수는 `static final` + `UPPER_SNAKE_CASE`, enum 상수도 `UPPER_SNAKE_CASE`.
- boolean 필드/메서드는 `isXxx`, `hasXxx`로 시작. `ApiResponse`처럼 JSON 키가 중요한 경우 `@JsonProperty`로 고정한다.
- 패키지는 단수형 소문자 (`dto`, `entity`, `enums`는 기존 코드를 따라 그대로 유지).

## 서식 / 작성 스타일

- 들여쓰기 4칸, UTF-8, 파일 끝에 개행. 포매터가 없으니 주변 파일의 서식을 그대로 따른다.
- 주석은 한국어로, "왜"를 적는다. 코드를 그대로 번역한 주석은 달지 않는다. 기존 코드처럼 비자명한 로직 위에 한두 줄로 설명한다.
- 외부에서 호출되는 비자명한 메서드(오버라이드 구현, 파싱 유틸 등)에는 기존 코드의 Javadoc 형식을 따른다.

```java
/***
 * 함수 기능: 카카오로부터 받은 사용자 정보를 파싱하여 KakaoResponse 객체로 만든다.
 * @param attributes 카카오로부터 얻은 사용자 정보
 * @return OAuth2Response
 * @throws AuthException 필수 동의 항목이 누락된 경우
 */
```
- 가능한 곳에서는 불변으로: 필드는 `final`, 외부로 넘기는 컬렉션은 `List.copyOf` / `Collectors.toUnmodifiableList`.
- `null`을 리턴하지 않는다. 값이 없을 수 있으면 `Optional`을 쓰거나 예외를 던진다.
- 체크 예외를 삼키지 않는다. 처리할 수 없으면 적절한 `ProjectException`으로 변환해 올린다.