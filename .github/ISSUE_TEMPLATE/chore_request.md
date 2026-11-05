---
name: 🔧 Chore
about: 빌드, 의존성, 설정 등 기타 작업을 위한 템플릿
title: "🔧[CHORE] "
labels: "chore"
assignees: ""
---

## 1. 작업 요약

<!-- 어떤 작업을 하는지 한 줄로 적어주세요. (의존성 추가, 설정 변경, CI 등) -->

-

## 2. 배경 / 목적

<!-- 왜 필요한지, 무엇이 막혀 있는지 적어주세요. -->

-

## 3. 작업 내용

<!-- 해당하는 항목에 체크하고 아래에 구체적으로 적어주세요. -->

- [ ] 의존성 추가/변경 (`build.gradle`)
- [ ] 설정 변경 (`application.yaml` / `application-<profile>.yaml`)
- [ ] 환경 변수 추가 (`.env` / `.env.example`)
- [ ] 빌드 / CI / 배포 설정
- [ ] 기타:

상세:
-
-

<!-- Spring Boot 4라서 스타터 이름이 Boot 3와 다릅니다 (spring-boot-starter-webmvc 등). -->

## 4. 영향 범위

- 영향받는 범위: (로컬 실행 / 빌드 / 테스트 / 배포)
- 관련 파일:
    - `파일경로1`
    - `파일경로2`
- `.env` 키 추가 여부: (추가한다면 `.env.example`에도 반영하고 팀에 공유)

## 5. 주의 사항

<!-- 다른 개발자의 로컬 환경이 깨질 수 있는 변경이라면 꼭 적어주세요. -->

-

## 6. 완료 조건 (Acceptance Criteria)

- [ ] `.\gradlew.bat build` 통과
- [ ] 로컬에서 애플리케이션 정상 기동
- [ ] 환경 변수를 추가했다면 `.env.example` 갱신 및 공유 완료
- [ ] 
