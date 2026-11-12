---
description: 성공/에러 코드와 예외 설계 — 코드 문자열 규칙, BaseErrorCode 구현, 예외 핸들러 확장
paths:
  - "src/main/java/com/sudam/global/exception/**"
  - "src/main/java/com/sudam/global/handler/**"
  - "src/main/java/com/sudam/**/exception/**"
---

# 에러 코드 / 예외 컨벤션

## 구조

**코드 enum은 항상 `<스코프>/exception/code/`, 예외 클래스는 그 한 단계 위 `<스코프>/exception/`에 둔다.**

```
global/exception/
├── ProjectException.java
└── code/
    ├── BaseErrorCode.java      # getStatus() / getCode() / getMessage()
    ├── BaseSuccessCode.java    # 동일 시그니처
    ├── GeneralErrorCode.java   # COMMON400 ~ COMMON503
    └── GeneralSuccessCode.java # COMMON200, COMMON201

domain/<도메인>/exception/
├── <도메인>Exception.java       # extends ProjectException
└── code/
    ├── <도메인>ErrorCode.java    # implements BaseErrorCode
    └── <도메인>SuccessCode.java  # implements BaseSuccessCode
```

- 도메인/관심사별 코드는 별도 enum이 `BaseErrorCode` / `BaseSuccessCode`를 구현한다 (예: `domain.auth.exception.code.AuthErrorCode`).
- **코드 enum은 도메인 자산이다.** 인증 코드도 `global/security`가 아니라 `domain/auth/exception/code/`에 둔다. `global/security`에는 필터·핸들러·유틸·토큰 저장소만 남긴다.
- 예외: 일반 비즈니스 예외는 `ProjectException`(생성자에 `BaseErrorCode`)**을 상속받는 각 도메인 예외로 변환해서** 던진다. 소셜 로그인 흐름만 `SocialLoginException`(`OAuth2AuthenticationException` 상속, `global/security/oauth2/exception/`)을 쓴다.

## 성공 코드 — 도메인별 enum을 쓴다

컨트롤러는 `GeneralSuccessCode`가 아니라 **해당 도메인의 `<도메인>SuccessCode`**로 응답한다. 어느 도메인의 응답인지 코드 문자열에서 바로 드러나고, 메시지를 행위에 맞게 쓸 수 있다.

```java
// domain/auth/exception/code/AuthSuccessCode.java
@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {

    TOKEN_REISSUED(HttpStatus.OK, "AUTH200_1", "토큰을 재발급했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

```java
return ApiResponse.onSuccess(AuthSuccessCode.TOKEN_REISSUED, result);
```

- 코드 문자열 규칙은 에러 코드와 같다: `<접두사><HTTP 상태>_<순번>` (`AUTH200_1`, `USER200_1`).
- 메시지는 `"성공적으로 요청을 처리했습니다."` 같은 범용 문구가 아니라 수행한 행위를 적는다 (`"회원 탈퇴가 완료되었습니다."`).
- `GeneralSuccessCode`는 도메인에 속하지 않는 범용 응답의 제자리로 남겨 둔다.

## 도메인 예외 — `ProjectException`을 상속한다

일반 비즈니스 예외는 `ProjectException`을 직접 던지지 않고, **도메인별 예외 클래스로 변환해서** 던진다. Member 도메인에서 터진 예외는 `MemberException extends ProjectException`으로 잡히게 하는 식이다.

```java
// domain/member/exception/MemberException.java
public class MemberException extends ProjectException {

    public MemberException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
```

```java
// 서비스
throw new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);
```

- 위치/이름: `domain.<도메인>.exception.<도메인>Exception`. 코드 enum은 그 아래 `code` 패키지(`domain.<도메인>.exception.code.<도메인>ErrorCode`)에 둔다.
- 생성자는 `BaseErrorCode` 하나만 받아 `super(errorCode)`로 넘긴다. 상태/코드/메시지는 전부 에러 코드 enum이 들고 있으므로 예외 클래스에 필드를 추가하지 않는다.
- 도메인 예외는 자기 도메인의 에러 코드와 짝지어 던지는 것이 기본이다. `MemberException`에 `PostErrorCode`를 넣지 않는다.
- `global` 공통 로직처럼 특정 도메인에 속하지 않는 예외만 `ProjectException`을 그대로 던진다.
- `GeneralExceptionHandler.handleProjectException`이 `ProjectException` 하위 타입을 모두 잡으므로, **도메인 예외를 추가해도 핸들러를 손댈 필요가 없다.** 도메인별 `@ExceptionHandler`를 새로 만들지 않는다.
- 도메인 예외를 만드는 이유는 응답이 달라지기 때문이 아니라 — 응답 포맷은 동일하다 — 스택트레이스와 호출부에서 **어느 도메인에서 터진 예외인지** 바로 드러나고, 필요할 때 도메인 단위로 catch할 수 있기 때문이다.

## 코드 문자열 규칙

- 형식: `<접두사><HTTP 상태>` + 같은 상태가 둘 이상일 때만 `_<순번>`.
  - 공통 코드는 상태당 하나뿐이므로 접미사 없음: `COMMON400`, `COMMON404`.
  - 도메인 코드는 상태당 여러 개가 생기므로 `_1`부터 붙인다: `AUTH400_1`, `AUTH401_1`, `AUTH401_2`.
- 접두사는 도메인/관심사 대문자 약어(`AUTH`, `USER`, ...). 새 도메인을 만들 때 접두사를 정하고 그 enum 안에서 일관되게 쓴다.
- 이미 배포된 코드 문자열은 클라이언트 계약이다. 의미를 바꾸려면 새 코드를 추가하고, 기존 코드는 두거나 명시적으로 제거한다.
- `message`는 사용자에게 보여줄 수 있는 한국어 한 문장. 스택트레이스·내부 식별자·SQL을 담지 않는다.

## 새 에러 코드를 추가할 때

1. 해당 도메인의 `exception/code` 패키지에 `XxxErrorCode` enum이 있는지 확인하고, 없으면 만든다.

```java
// domain/member/exception/code/MemberErrorCode.java
@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404_1", "해당 회원을 찾을 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

2. 같은 패키지에 `<도메인>Exception extends ProjectException`이 있는지 확인하고, 없으면 만든다 (위 "도메인 예외" 절 참고).
3. 서비스에서 `throw new MemberException(MemberErrorCode.MEMBER_NOT_FOUND);`로 던진다.
4. 상태 코드를 `GeneralErrorCode`에 없는 값으로 쓰려면, 스프링 표준 예외가 그 상태로 떨어질 수 있는지 확인한다 (`resolveErrorCode`가 매핑 테이블에 없는 상태를 `COMMON500`으로 떨어뜨린다).

`GeneralErrorCode`에는 상태값당 하나의 상수만 둔다. `GeneralExceptionHandler.ERROR_CODE_BY_STATUS`가 `HttpStatus.value()`를 키로 `toUnmodifiableMap`을 만들기 때문에, 같은 상태를 가진 상수를 두 개 넣으면 **클래스 초기화 시 중복 키 `IllegalStateException`이 발생해 핸들러 빈 생성이 실패한다.** 상태당 여러 공통 메시지가 필요하면 `GeneralErrorCode`가 아니라 도메인 전용 enum에 추가한다.

## 예외 처리 (`GeneralExceptionHandler`)

`@RestControllerAdvice` + `ResponseEntityExceptionHandler` 상속 구조다. 계층은 아래 순서로 동작한다.

| 대상 | 처리 지점 |
| --- | --- |
| `ProjectException` 및 하위 도메인 예외(`MemberException` 등) | `handleProjectException` — 코드의 status/code/message로 응답 |
| `@RequestBody`·`@ModelAttribute` 검증/바인딩 실패 | `handleMethodArgumentNotValid` — `result`에 `{필드명: 메시지}` |
| 단일 파라미터 타입 변환 실패 | `handleTypeMismatch` — `result`에 `{파라미터명: "타입이 올바르지 않습니다."}` |
| 단일 파라미터 검증 실패 | `handleHandlerMethodValidationException` |
| 그 외 스프링 표준 예외(깨진 JSON, 404/405/415, 필수 파라미터 누락 등) | `handleExceptionInternal` — 상태 → 공통 코드 역매핑 |
| 미분류 예외 | `handleGlobalException` — `COMMON500` |

- 컨트롤러·서비스에서 `ResponseEntity`로 에러를 직접 만들지 않는다. 예외를 던지고 핸들러가 포맷을 맡는다.
- 핸들러를 새로 추가할 때는 로그 레벨 규칙을 지킨다: 클라이언트 잘못(4xx)은 `log.warn`, 서버 잘못(5xx)은 예외 객체를 함께 넘기는 `log.error`.
- 로그 메시지는 `[말머리] code={}, message={}` 형태를 유지한다.
- 새 `@ExceptionHandler`를 추가하기 전에, 이미 `ResponseEntityExceptionHandler`가 잡는 예외인지 확인한다. 중복으로 등록하면 어느 쪽이 이기는지 불분명해진다.