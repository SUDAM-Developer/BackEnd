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
- 제약은 DB에도 걸린다: `nullable`, `length`, `unique`를 `@Column`/`@Table(uniqueConstraints = ...)`에 선언한다 (실제 스키마는 마이그레이션 SQL이 만들므로 양쪽을 같게 맞춘다).
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

스키마는 Flyway만 바꾼다. Hibernate는 `ddl-auto: validate`로 검사만 하며, 어느 프로파일에서도 `create` · `update`로 바꾸지 않는다.

- 위치는 `src/main/resources/db/migration`, 이름은 `V{버전}__{설명}.sql`(언더스코어 2개). 버전은 1부터 증가하는 정수, 설명은 영문 소문자 snake_case — `V2__add_users_nickname.sql`.
- 엔티티 변경과 마이그레이션 SQL을 **같은 PR**에 담는다. 엔티티만 바꾸면 기동이 실패한다.
- 이미 머지된 파일은 수정 · 삭제 · 이름 변경하지 않는다(체크섬 검증 실패로 기동되지 않는다). 고칠 것이 있으면 새 버전을 추가한다.
- 머지 전 최신 `dev`의 마지막 버전을 확인한다. 번호가 겹치거나 더 낮으면 나중에 머지하는 쪽이 번호를 올린다.
- enum 컬럼은 MySQL `ENUM`이다. Java enum에 값을 추가하면 `ALTER TABLE ... MODIFY` 마이그레이션도 추가한다.
- `validate`는 FK · UNIQUE · 인덱스 누락을 잡지 못한다. 엔티티와 SQL이 같은지 리뷰에서 확인한다.
- 제약 이름은 직접 붙인다: `fk_<테이블>_<컬럼>`, `uk_<테이블>_<컬럼>`, `idx_<테이블>_<컬럼>`. 해시 이름에 의존하지 않는다.
- MySQL은 DDL을 롤백하지 못한다. 파일 하나에는 작은 단위의 변경만 담는다.
- 데이터가 있는 테이블에 NOT NULL 컬럼을 더할 때는 기존 행을 채우는 방법(DEFAULT 또는 단계별 UPDATE)을 함께 적는다.