---
description: API 작성 규칙 — 컨트롤러/DTO 설계, ApiResponse 응답 포맷, URL·HTTP 메서드, 요청 검증
paths:
  - "src/main/java/com/sudam/domain/**/controller/**"
  - "src/main/java/com/sudam/domain/**/dto/**"
  - "src/main/java/com/sudam/global/ApiResponse.java"
---

# API 작성 규칙

## 응답 포맷은 `ApiResponse` 하나로 통일한다

모든 성공/실패 응답은 `com.sudam.global.ApiResponse`로 감싼다. 직렬화 순서는 `isSuccess`, `code`, `message`, `result`로 고정되어 있다.

**반환 타입은 `ApiResponse<T>`를 그대로 쓴다. `ResponseEntity`로 감싸는 것은 201 Created뿐이다.**

```java
// 200 — ApiResponse 직접 반환. Spring이 기본 200으로 직렬화한다
@GetMapping("/{userId}")
public ApiResponse<UserResponse> find(@PathVariable Long userId) {
    return ApiResponse.onSuccess(UserSuccessCode.FOUND, userService.find(userId));
}
```

```java
// 201 — 상태를 바꿔야 하므로 ResponseEntity를 쓴다
@PostMapping
public ResponseEntity<ApiResponse<UserCreateResponse>> create(@Valid @RequestBody UserCreateRequest request) {
    UserCreateResponse response = userService.create(request);
    return ResponseEntity
            .status(UserSuccessCode.CREATED.getStatus())
            .body(ApiResponse.onSuccess(UserSuccessCode.CREATED, response));
}
```

- 결과 데이터가 없으면 `ApiResponse<Void>` + `ApiResponse.onSuccess(code)` 오버로딩을 쓴다.
- **성공 코드는 `GeneralSuccessCode`가 아니라 도메인별 `<도메인>SuccessCode`를 쓴다** (`domain/<도메인>/exception/code/`). 상세는 `error-code-convention.md`.
- 201에서 HTTP 상태는 성공 코드 enum의 `getStatus()`에서 가져온다. 상태 코드를 문자열/숫자로 직접 쓰지 않는다.
- 쿠키처럼 헤더를 손봐야 할 때는 `HttpServletResponse`를 파라미터로 받아 심는다. 그 때문에 `ResponseEntity`로 바꾸지 않는다.
- 실패 응답은 컨트롤러에서 만들지 않는다. `ApiResponse.onFailure(...)`는 `GeneralExceptionHandler`의 몫이다 → `error-code-convention.md`.

## 컨트롤러

- `@RestController` + `@RequiredArgsConstructor`, 의존성은 서비스만 `private final`로 받는다.
- 클래스에 `@RequestMapping("/api/v1/<자원복수형>")`을 두고 메서드에는 하위 경로만 붙인다.
- 컨트롤러에는 비즈니스 로직도 try-catch도 두지 않는다. 입력 바인딩/검증 → 서비스 호출 → 응답 조립, 이 세 가지만 한다.
- 조회 결과가 여러 건이면 페이징을 기본으로 고려한다. `Pageable`을 그대로 노출하지 말고 `page`, `size` 파라미터를 받아 서비스에서 변환한다.

## URL / HTTP 메서드

- 경로는 소문자 kebab-case, 자원은 복수형 명사: `/api/v1/study-rooms`.
- 경로에 동사를 넣지 않는다. 행위는 HTTP 메서드로 표현한다 — 조회 `GET`, 생성 `POST`, 전체 수정 `PUT`, 부분 수정 `PATCH`, 삭제 `DELETE`.
  - **예외: 인증 행위는 동사를 허용한다** (`/api/v1/auth/reissue`, `/api/v1/auth/logout`). 토큰 재발급·로그아웃은 자원 CRUD로 표현하기 어렵고 관례적으로 동사를 쓴다. 이 예외를 다른 도메인으로 넓히지 않는다.
- 식별자는 경로 변수(`/{userId}`), 필터·정렬·페이징은 쿼리 파라미터.
- 버전은 경로에 `v1`로 둔다. 호환이 깨지는 변경은 `v2`를 새로 만든다.

## DTO

- 요청은 `dto/request`, 응답은 `dto/response`에 두고 이름은 `<행위><자원>Request` / `...Response`.
- DTO는 `record` 또는 `@Getter` + `@Builder` 클래스로 만들고 불변으로 둔다. 엔티티를 요청/응답 타입으로 쓰지 않는다.
- 엔티티 → 응답 변환은 응답 DTO의 정적 팩토리(`public static XxxResponse from(Xxx entity)`)에 둔다.
- 응답에 비밀번호·토큰·소셜 ID 같은 민감 값을 넣지 않는다.
  - **예외: 토큰 재발급 응답의 액세스 토큰**(`TokenReissueResponse`). 로그인은 리다이렉트라 URI 프래그먼트로 보내지만 재발급은 일반 요청이어서 본문이 유일한 전달 경로다. 리프레시 토큰은 이때도 본문에 넣지 않고 `HttpOnly` 쿠키로만 내려준다.

## 요청 검증

- Bean Validation(`spring-boot-starter-validation`)을 쓴다. `@RequestBody`에는 `@Valid`, `@ModelAttribute`에도 `@Valid`, 단일 파라미터 제약(`@RequestParam @Min(1) int page`)을 쓰려면 클래스에 `@Validated`를 붙인다.
- 제약 애너테이션의 `message`는 **한국어**로 직접 적는다. 이 메시지가 그대로 클라이언트에 전달된다 (`GeneralExceptionHandler`가 필드명 → 메시지 Map을 `result`에 담는다).
- 형식 검증(널/길이/범위/패턴)은 DTO 애너테이션으로, 비즈니스 규칙 검증(중복·권한·상태 전이)은 서비스에서 예외로 처리한다.

## API를 추가·변경했으면

`docs/api-spec.md`의 해당 엔드포인트 항목을 같은 PR에서 갱신한다.