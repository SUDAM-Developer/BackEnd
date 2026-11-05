---
description: 테스트 작성법 — 슬라이스 선택, 네이밍, given/when/then, DB 의존 테스트 주의점
paths:
  - "src/test/**"
  - "build.gradle"
---

# 테스트 컨벤션

## 먼저 알아야 할 전제

- 이 프로젝트에는 H2 폴백도 Testcontainers도 없다. `@SpringBootTest`는 `.env`의 **실제 MySQL에 연결된다.** `ddl-auto: create`이므로 테스트를 돌리면 그 DB 스키마가 drop/recreate된다.
- `SudamApplicationTests`(`contextLoads`)는 전체 컨텍스트 로드 테스트다. DB가 없으면 이 테스트부터 실패하는데, 코드 문제가 아니라 환경 문제다.
- 그래서 **기본 선택은 컨텍스트를 띄우지 않는 단위 테스트**다. 컨텍스트가 필요하면 전체(`@SpringBootTest`)보다 슬라이스를 쓴다.

## 슬라이스 선택

| 대상 | 애너테이션 | 비고 |
| --- | --- | --- |
| 서비스·도메인 로직 | 없음 (순수 JUnit + Mockito) | 가장 선호. `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks` |
| 컨트롤러 (요청 바인딩·검증·응답 포맷) | `@WebMvcTest(XxxController.class)` | 서비스는 `@MockitoBean`으로 대체 |
| 레포지토리·JPA 매핑 | `@DataJpaTest` | 실제 MySQL에 붙는다. 데이터 전제를 깔고 시작할 것 |
| 애플리케이션 전체 배선 | `@SpringBootTest` | 꼭 필요할 때만 |

테스트 지원 의존성은 모듈별로 쪼개져 있다 (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`). 다른 슬라이스가 필요하면 `spring-boot-starter-test`를 끌어오지 말고 해당 모듈의 `-test` 스타터를 `build.gradle`에 추가한다.

## 작성 규칙

- 위치는 프로덕션 코드와 같은 패키지, 이름은 `<대상클래스>Test`. 통합 성격이면 `<대상>IntegrationTest`.
- 테스트 메서드 이름은 한국어로 상황과 기대 결과를 적고 `@DisplayName`을 붙인다.

```java
@Test
@DisplayName("존재하지 않는 사용자를 조회하면 USER404_1 예외가 발생한다")
void 존재하지_않는_사용자_조회_시_예외() {
    // given
    given(userRepository.findById(1L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> userService.findById(1L))
            .isInstanceOf(ProjectException.class)
            .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
            .isEqualTo("USER404_1");
}
```

- 본문은 `// given`, `// when`, `// then` 주석으로 구분한다.
- 단정은 AssertJ(`assertThat`, `assertThatThrownBy`)를 쓴다. JUnit `Assertions`와 섞지 않는다.
- 테스트 하나당 하나의 행위를 검증한다. 입력만 다른 반복은 `@ParameterizedTest`로 묶는다.
- 예외 검증은 메시지 문자열이 아니라 **에러 코드**(`getErrorCode().getCode()`)로 한다. 메시지는 바뀔 수 있다.
- API 테스트는 `ApiResponse` 포맷까지 확인한다: `isSuccess`, `code`, 그리고 검증 실패 시 `result`의 `{필드명: 메시지}`.
- 각 테스트는 독립적으로 통과해야 한다. 실행 순서에 의존하지 않고, DB를 쓰는 테스트는 `@Transactional`(롤백)이나 명시적 정리로 상태를 남기지 않는다.
- 랜덤·현재 시각에 의존하지 않는다. 시간이 필요하면 고정 값을 주입한다.

## 무엇을 테스트하는가

- 반드시: 비즈니스 규칙과 분기, 예외/에러 코드 매핑, 요청 검증 애너테이션, 엔티티의 상태 변경 메서드.
- 하지 않아도 되는 것: 게터/빌더, 프레임워크 자체 동작, 단순 위임 메서드.

## 실행

```powershell
.\gradlew.bat test
.\gradlew.bat test --tests "com.sudam.domain.user.service.UserServiceTest"
```

리포트는 `build/reports/tests/test/index.html`. 테스트가 실패하면 실패 사실과 출력을 그대로 보고한다 — 통과한 것처럼 요약하지 않는다.
