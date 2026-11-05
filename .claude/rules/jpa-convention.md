---
description: JPA 엔티티·레포지토리 규칙 — 매핑, 연관관계, 지연 로딩과 N+1, 트랜잭션 경계
paths:
  - "src/main/java/com/sudam/domain/**/entity/**"
  - "src/main/java/com/sudam/domain/**/repository/**"
  - "src/main/java/com/sudam/domain/**/service/**"
  - "src/main/resources/application*.yaml"
---

# JPA 컨벤션

## 엔티티

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder
    private User(String nickname, Role role) {
        this.nickname = nickname;
        this.role = role;
    }
}
```

- 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 막는다. 생성은 `@Builder`를 붙인 private 생성자나 정적 팩토리 메서드로만.
- `@Setter` 금지. 상태 변경은 의도를 담은 메서드(`changeNickname`, `withdraw`)로 표현한다.
- PK는 `Long` + `GenerationType.IDENTITY` (MySQL `AUTO_INCREMENT`).
- enum 필드는 반드시 `@Enumerated(EnumType.STRING)`. 서수 저장은 값 추가 시 데이터가 깨진다.
- 테이블/컬럼명은 snake_case. `user`, `order` 같은 예약어 충돌을 피해 `@Table(name = "users")`처럼 명시한다.
- 제약은 DB에도 걸린다: `nullable`, `length`, `unique`를 `@Column`/`@Table(uniqueConstraints = ...)`에 선언한다 (`ddl-auto: create`라 스키마가 엔티티에서 생성된다).
- `equals`/`hashCode`를 Lombok으로 생성하지 않는다. 필요하면 ID 기반으로 직접 구현한다.
- 생성/수정 시각이 필요하면 `BaseTimeEntity`(`@MappedSuperclass` + `@EntityListeners(AuditingEntityListener.class)`)를 `global`에 만들어 상속한다. 이때 `@EnableJpaAuditing`도 설정 클래스에 추가해야 한다.

## 연관관계

- `@ManyToOne` / `@OneToOne`은 **항상** `fetch = FetchType.LAZY`로 명시한다 (기본이 EAGER).
- 연관관계의 주인은 FK를 가진 쪽. 반대편 `@OneToMany`는 꼭 필요할 때만 `mappedBy`로 추가한다.
- `CascadeType.ALL` / `orphanRemoval`은 생명주기가 실제로 종속된 경우에만. 애매하면 쓰지 않는다.
- 양방향을 만들면 연관관계 편의 메서드를 엔티티에 두어 양쪽 동기화를 한 곳에서 처리한다.

## 레포지토리 / 조회

- `JpaRepository<T, Long>`을 상속한 인터페이스. 메서드 이름 쿼리는 짧을 때만, 길어지면 `@Query`(JPQL)로 쓴다.
- 단건 조회는 `Optional`로 받아 `orElseThrow(() -> new ProjectException(XxxErrorCode.XXX_NOT_FOUND))`로 처리한다.
- N+1 방어: 컬렉션은 `default_batch_fetch_size: 100`(이미 설정됨)에 의존하고, 단건/소수 연관은 `@EntityGraph` 또는 `join fetch`를 쓴다. 페이징 쿼리에 컬렉션 `join fetch`를 쓰지 않는다.
- 네이티브 쿼리는 최후 수단. 쓸 경우 왜 JPQL로 안 되는지 주석으로 남긴다.

## 트랜잭션

- 트랜잭션 경계는 **서비스 레이어**. 컨트롤러나 레포지토리에 `@Transactional`을 붙이지 않는다.
- 클래스에 `@Transactional(readOnly = true)`를 걸고, 쓰기 메서드에만 `@Transactional`을 재선언한다.
- `open-in-view: false`이므로 **컨트롤러/직렬화 시점에는 지연 로딩이 불가능하다.** 트랜잭션 안(서비스)에서 필요한 데이터를 모두 꺼내 DTO로 변환해 반환한다.
- 트랜잭션 안에서 외부 API 호출·파일 업로드 같은 느린 I/O를 하지 않는다.

## 스키마 주의

`ddl-auto: create`라서 부팅마다 대상 DB가 drop/recreate된다. 엔티티 변경 시 마이그레이션은 필요 없지만, 로컬 데이터는 매번 날아간다는 전제로 작업한다. 공유 DB를 가리키지 말 것.