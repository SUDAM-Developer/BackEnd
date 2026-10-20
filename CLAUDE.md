# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Fresh Spring Initializr scaffold ("sudam", a backend per `README.md`). Only `SudamApplication` and its context-load test exist — there is no domain code, no controllers, no entities yet. Expect to create package structure rather than find it.

## Commands

Use the wrapper (`gradlew.bat` on Windows, `./gradlew` elsewhere):

```powershell
.\gradlew.bat build                                  # compile + test
.\gradlew.bat bootRun                                # run the app
.\gradlew.bat test                                   # all tests
.\gradlew.bat test --tests "com.sudam.SudamApplicationTests"        # single class
.\gradlew.bat test --tests "com.sudam.SudamApplicationTests.contextLoads"  # single method
```

Test HTML report lands at `build/reports/tests/test/index.html`. No linter or formatter is configured.

## Datasource comes from a git-ignored `.env`

`application.yaml` imports `optional:file:.env[.properties]` and resolves `spring.datasource.*` from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. `.env` is git-ignored; `.env.example` lists the required keys.

Because the import is `optional:`, a missing `.env` does **not** fail fast with a clear message — startup instead dies on unresolved `${DB_URL}` with `DataSourceProperties$DataSourceBeanCreationException`. If you hit that, the cause is almost always a missing or incomplete `.env`, not broken code.

`SudamApplicationTests` is a full `@SpringBootTest`, so **tests need a reachable MySQL too** — there is no H2 fallback or Testcontainers setup. `ddl-auto: create` means every boot drops and recreates the schema in the target database, so never point `DB_URL` at anything you care about.

## Spring Boot 4.1.1 — starter names differ from Boot 3

This project is on Spring Boot 4.1.1 / Java 21 toolchain / Gradle 9.8.1. Boot 4 renamed and split the starters, so the Boot 3 names most examples use are wrong here:

| Don't use (Boot 3) | Use (Boot 4, as in `build.gradle`) |
| --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` | `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test` |

Test support is per-module rather than one umbrella starter: if a new test needs a slice whose module isn't already a `testImplementation` dependency, add that module's `-test` starter instead of reaching for `spring-boot-starter-test`.

## Conventions

- Base package `com.sudam` (Gradle `group` is just `com`, so don't infer `com.sudam` from the group).
- Lombok is wired for both main and test source sets (`compileOnly` + `annotationProcessor`).
- Config lives in `application.yaml`, not `.properties`.
- `HELP.md` is git-ignored generated Initializr output — don't edit or reference it.

## GitHub templates (Korean)

`.github/` carries issue and PR templates written in Korean; match that language and structure when drafting issues or PRs.

- PR titles: `[FEAT]`, `[BUGFIX]` — e.g. `[FEAT] 회원가입 API 추가`
- Issue titles: `✨[FEAT]`, `🐞[BUG]`, `♻️[REFACTOR]`, auto-labeled `feature` / `bug` / `refactor`
- Both PR and issue templates require a 완료 조건 / Acceptance Criteria checklist and an 영향 범위 (affected domain + package paths) section.
