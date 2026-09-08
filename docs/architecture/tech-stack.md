# Phase 5. 기술 스택 및 구현 아키텍처

> 상태: **Accepted**
>
> 제품 정책 기준: [`../00-project-baseline.md`](../00-project-baseline.md)
>
> 데이터 기준: [`erd.md`](erd.md)
>
> API 기준: [`../api/rest-api.md`](../api/rest-api.md)
>
> 작성일: 2026-08-30
>
> 확정일: 2026-08-30

## 1. 이번 단계의 목적

TrackPick MVP를 구현하기 전에 언어와 framework 이름뿐 아니라 DB, session, migration, API 계약, 외부 API client, scheduler, 테스트와 배포 단위까지 결정한다.

사용자는 PostgreSQL 경험이 있고 Java를 새로 학습하려 한다. 따라서 Java 생태계의 핵심을 실제로 경험하면서도, 개인 포트폴리오 프로젝트 규모에 맞는 모듈형 모놀리스로 구성한다.

## 2. 결정해야 할 사항

1. Backend와 frontend의 언어, framework와 지원 버전
2. build tool과 repository 구조
3. PostgreSQL 버전, PK와 문자열 정렬 정책
4. ORM과 직접 SQL의 경계
5. 인증, session, CSRF와 password hashing 방식
6. Apple iTunes Search API adapter의 호출 정책
7. API 명세의 단일 기준과 frontend type 생성 방식
8. Daily Ranking scheduler와 중복 실행 방지 방식
9. 자동화 테스트 계층과 실제 PostgreSQL 검증 방식
10. 로컬 실행과 production packaging 방식

## 3. 추천 설계 요약

| 영역 | 선택 |
| --- | --- |
| Architecture | 하나의 repository와 하나의 backend application을 사용하는 모듈형 모놀리스 |
| Backend | Java 25 LTS, Spring Boot 4.1.x, Spring MVC |
| Backend build | Maven Wrapper 3.9.x |
| Data access | Spring Data JPA + 복잡한 집계용 Spring `JdbcClient` |
| Database | PostgreSQL 18 최신 minor, UTF-8, ICU collation |
| Migration | Flyway SQL migration, Hibernate DDL 생성 금지 |
| Identifier | PostgreSQL `uuid`, application에서 UUID v4 생성 |
| Security | Supabase Auth 이메일 인증 + Spring Security 7 server-side session, CSRF 활성화 |
| Session | Spring Session JDBC를 PostgreSQL에 저장 |
| Password | Supabase Auth가 저장·검증하고 TrackPick DB에는 자격 증명을 저장하지 않음 |
| External API | Spring `RestClient` 기반 Apple iTunes Search API adapter |
| Frontend | Node.js 24 LTS, React 19.2, TypeScript, Vite 8 |
| Routing/state | React Router 8 Declarative Mode, TanStack Query 5 |
| Styling | CSS Modules + CSS custom properties, Lucide React icons |
| API contract | OpenAPI 3.1 YAML contract-first, frontend type 자동 생성 |
| Scheduler | Spring `@Scheduled` + `ranking_runs` DB claim/Unique Constraint |
| Backend test | JUnit 6, AssertJ, Spring test, Testcontainers PostgreSQL, WireMock |
| Frontend test | Vitest, React Testing Library, MSW, Playwright |
| Local environment | Docker Compose로 PostgreSQL만 실행, backend/frontend는 개발 process로 실행 |
| Production package | frontend build를 Spring Boot 정적 리소스에 포함한 단일 Docker image + PostgreSQL |
| CI | GitHub Actions에서 lint, build, unit/integration test, OpenAPI validation 수행 |

정확한 patch 버전은 프로젝트 생성 시 lock file과 Maven dependency management에 고정한다. 문서에는 호환성 기준이 되는 major/minor만 유지하고 보안·버그 수정 patch는 자동화된 의존성 업데이트로 올린다.

## 4. Backend

### 4.1 Java 25 LTS와 Spring Boot 4.1

[JDK 25는 최신 Java LTS](https://www.oracle.com/java/technologies/downloads/)이고, [Spring Boot 4.1은 Java 25를 지원](https://docs.spring.io/spring-boot/system-requirements.html)한다. 새 프로젝트에서 이전 major를 선택해 즉시 migration 과제를 만들 이유가 없으므로 이 조합을 사용한다.

선택 이유:

- 사용자의 Java 학습 목표에 직접 부합한다.
- Spring Security, transaction, scheduler, validation과 외부 HTTP client를 하나의 일관된 생태계에서 배울 수 있다.
- TrackPick의 핵심인 DB transaction, Unique Constraint, batch와 동시성 설명에 적합하다.
- Java record, sealed type 등 현대 Java 문법을 DTO와 domain result에 활용할 수 있다.

Trade-off:

- Java와 Spring을 동시에 처음 배우므로 초기 학습량이 크다.
- Spring Boot 4는 이전 3.x tutorial과 package/version 차이가 있을 수 있으므로 4.1 공식 문서를 기준으로 한다.

### 4.2 Spring MVC

TrackPick의 DB와 외부 API 호출은 전형적인 request/response 흐름이다. Reactive stack을 도입해 얻는 이익보다 transaction과 debugging 복잡도가 커지므로 Spring WebFlux가 아닌 Spring MVC를 사용한다.

Apple API 호출도 blocking `RestClient`로 구성하고 연결·응답 timeout을 명시한다. 외부 API 장애는 adapter에서 내부 오류로 변환한다.

### 4.3 Maven Wrapper

Java를 처음 배우는 사용자가 dependency와 lifecycle을 명시적으로 읽기 쉬운 Maven을 사용한다. Gradle Kotlin DSL을 선택하면 Java 외에 Kotlin 기반 build 문법까지 동시에 학습해야 하므로 채택하지 않는다.

모든 환경은 repository의 Maven Wrapper를 사용하고 전역 Maven 설치에 의존하지 않는다.

### 4.4 모듈 경계

Backend package는 기술 계층 전체를 한곳에 모으지 않고 feature 기준으로 구성한다.

```text
com.trackdrop
  identity
  catalog
  recommendation
  ranking
  moderation
  shared
```

각 feature 안에서 필요할 때만 `api`, `application`, `domain`, `infrastructure` 하위 package를 둔다. 작은 feature에 빈 계층 package를 미리 만들지 않는다.

Spring Modulith와 별도 microservice는 MVP에 도입하지 않는다. module 간 참조 규칙은 package 구조와 architecture test로 검증한다.

## 5. Database와 Data Access

### 5.1 PostgreSQL 18

[PostgreSQL 18은 현재 지원되는 최신 major이며 2030년까지 지원](https://www.postgresql.org/support/versioning/)된다. 사용자의 기존 경험을 살리고 ERD가 요구하는 transaction, window function, partial index와 `ON CONFLICT`를 직접 활용한다.

MongoDB는 Recommendation, Vote, quota, Ranking snapshot 사이의 강한 관계와 Unique Constraint를 표현하는 데 이점이 없으므로 사용하지 않는다. 테스트용 H2도 PostgreSQL의 collation, window function과 동시성 동작을 다르게 만들 수 있어 사용하지 않는다.

### 5.2 Migration과 schema 관리

- Flyway SQL migration이 schema의 유일한 기준이다.
- 운영과 test에서 `ddl-auto=validate`를 사용한다.
- 개발 편의를 위한 `create`, `create-drop`, `update`는 사용하지 않는다.
- seed Genre는 migration으로 삽입하며 code, display name과 sort order를 명시한다.
- Spring Session schema도 별도 Flyway migration으로 관리한다.

### 5.3 UUID

모든 Entity PK는 PostgreSQL `uuid`로 저장하고 Java `UUID.randomUUID()`로 UUID v4를 생성한다.

장점:

- 외부에 연속 ID를 노출하지 않는다.
- application에서 transaction 전에 ID를 확보할 수 있다.
- 단일 DB MVP 규모에서 UUID v4 index locality는 운영상 문제가 되지 않는다.

UUID v7과 sequence 기반 bigint는 실제 규모에서 index locality 또는 저장 공간이 문제가 될 때 다시 검토한다.

### 5.4 문자열 정규화와 정렬

- DB encoding은 UTF-8이다.
- 이메일은 application에서 trim, Unicode normalization과 case normalization을 수행한 별도 normalized column에 Unique Constraint를 건다.
- 화면 표시용 원문과 비교용 normalized 값을 분리한다.
- Track 순위는 PostgreSQL ICU 기반 case-insensitive collation과 마지막 `track_id` tie-break로 결정한다.
- migration에서 `und-u-ks-level2`, `deterministic=false` 기반 전용 collation을 생성하고 실제 Docker image에서 정렬 fixture test를 수행한다.

[PostgreSQL은 ICU 기반 nondeterministic collation으로 case-insensitive 비교를 지원](https://www.postgresql.org/docs/18/sql-createcollation.html)한다. ICU version 변경은 index 순서에 영향을 줄 수 있으므로 DB major/image 변경 시 collation version 검사와 `REINDEX` 절차를 운영 문서에 포함한다.

### 5.5 JPA와 SQL의 경계

Spring Data JPA를 다음에 사용한다.

- User, Track, Recommendation, Vote와 Report의 lifecycle
- 단순한 aggregate 조회
- transaction 경계와 optimistic state check

Spring `JdbcClient` 또는 `JdbcTemplate`을 다음에 사용한다.

- quota 조건부 atomic update
- `ROW_NUMBER` live chart query
- Ranking snapshot bulk insert
- `INSERT ... ON CONFLICT` 기반 job claim
- JPA로 표현하면 SQL 의도가 흐려지는 projection

[Spring `JdbcClient`는 named/positional parameter query를 제공](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html)하며, batch insert처럼 더 낮은 제어가 필요한 곳은 `JdbcTemplate`을 사용한다.

모든 것을 JPA로 숨기거나 모든 CRUD를 직접 SQL로 작성하지 않는다. 데이터 무결성에 중요한 SQL은 migration과 repository test에서 명시적으로 검증한다.

## 6. 인증과 보안

### 6.1 Supabase Auth, Spring Security와 JDBC Session

회원가입, 이메일 확인과 비밀번호 검증·재설정은 Supabase Auth에 위임한다. TrackPick backend는 이메일 로그인 성공 후 `HttpOnly`, `Secure`, `SameSite=Lax` cookie 기반 application session을 만들고 [Spring Session JDBC](https://docs.spring.io/spring-session/reference/guides/boot-jdbc.html)를 통해 PostgreSQL에 저장한다.

선택 이유:

- 브라우저 JavaScript에 bearer token을 저장하지 않는다.
- logout과 session 무효화가 명확하다.
- Redis 없이도 여러 application instance가 session을 공유할 수 있다.
- 사용자의 기존 PostgreSQL 지식으로 session 상태를 추적할 수 있다.

JWT는 발급 후 강제 만료와 보안 정책이 복잡해지고 현재 same-origin web application에 이점이 없어 사용하지 않는다.

### 6.2 Password

TrackPick DB에는 원문 비밀번호와 비밀번호 hash를 저장하지 않는다. backend는 가입·로그인 요청을 Supabase Auth API로 전달하며, application log에는 비밀번호나 이메일을 기록하지 않는다.

Password 입력은 8~16 code point로 제한하고 영문자와 숫자를 각각 하나 이상 요구한다. 공백과 제어문자는 거부하며 특수문자는 별도 whitelist 없이 허용한다. 입력을 trim하거나 대소문자 변환하지 않고 Supabase Auth에 전달한다.

### 6.3 Browser security

- 모든 state-changing endpoint에 CSRF token을 요구한다.
- production은 frontend와 API를 same-origin으로 배포한다.
- CORS는 production에서 불필요하게 전체 origin에 열지 않는다.
- session fixation 방지, logout invalidation과 cookie rotation은 Spring Security 기본 기능을 사용한다.
- login과 signup에는 IP 및 normalized account 기준 rate limit을 적용한다.
- 공개 오류에서 ID 또는 이메일 존재 여부를 과도하게 구분하지 않는다.
- login과 signup은 IP당 분당 30회로 제한한다. 한 IP에서 한 시간 안에 성공한 가입 5개를 허용하고 다음 가입 시도부터 24시간 차단하되, 실패한 가입은 성공 횟수에 포함하지 않는다.
- 로그인 실패 횟수로 계정을 잠그지 않는다. 일반 login은 session cookie, 유지 login은 인증 요청마다 만료가 7일 뒤로 이동하는 persistent session cookie를 사용한다.

## 7. Apple Catalog Adapter

Apple iTunes Search API 요청은 backend만 수행한다.

```text
country=KR
fallback discovery country=US when the KR result is empty
resolve every displayed fallback ID through country=KR lookup
media=music
entity=song
limit=20
explicit=Yes
```

[Apple Search API는 약 20 calls/minute 제한을 안내](https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/Searching.html)하므로 다음 정책을 사용한다.

- frontend는 입력할 때마다 검색하지 않고 명시적 검색 또는 debounce 후 제출한다.
- backend는 normalized query와 storefront를 key로 짧은 metadata cache를 사용한다.
- MVP local cache는 Caffeine을 사용하고 preview binary는 캐시하지 않는다.
- 동일 query 동시 요청은 가능한 경우 하나의 provider 호출로 합친다.
- adapter에 timeout, 보수적인 rate limit과 `MUSIC_PROVIDER_UNAVAILABLE` 변환을 둔다.
- Recommendation 제출 시 external Track ID를 lookup하여 metadata를 다시 검증한다.
- provider 원본 응답 전체와 credential을 DB에 저장하지 않는다.

Redis는 이 cache 때문에 도입하지 않는다. 다중 instance에서 provider rate limit을 공유해야 할 규모가 되면 Phase 13에서 Redis 또는 gateway rate limit을 검토한다.

## 8. Frontend

### 8.1 React SPA

[React의 현재 major는 19.2](https://react.dev/versions)이고 [Vite 8은 2026년 3월 stable로 출시](https://vite.dev/blog/announcing-vite8)됐다. Node.js는 production toolchain에 [LTS 사용이 권장](https://nodejs.org/en/about/previous-releases)되므로 Node.js 24 LTS를 사용한다.

TrackPick은 검색과 차트 중심의 authenticated SPA이고 SSR이나 검색 엔진 유입이 핵심이 아니다. Next.js 또는 React Router Framework Mode의 server runtime을 추가하지 않고 Vite 기반 React SPA로 만든다.

### 8.2 Frontend libraries

- React Router 8 Declarative Mode: URL과 화면 이동만 담당
- TanStack Query 5: server state, cache invalidation과 retry 담당
- React Hook Form: 가입, 로그인과 Recommendation form
- Zod: form과 외부 경계 validation 보조
- CSS Modules: component style 격리
- Lucide React: interface icon

전역 client state library는 처음부터 추가하지 않는다. 인증 사용자와 quota도 server state이므로 TanStack Query로 관리하고, player처럼 화면 전체에서 공유해야 하는 작은 상태만 React Context를 사용한다.

Tailwind CSS와 대형 component library는 MVP의 고유한 포트폴리오 UI를 직접 설계하고 학습 표면을 줄이기 위해 사용하지 않는다.

## 9. API Contract

[OpenAPI 3.1](https://spec.openapis.org/oas/v3.1.0)을 YAML로 작성하고 HTTP 계약의 단일 기준으로 사용한다.

- 경로: `docs/api/openapi.yaml`
- `rest-api.md`는 설계 이유와 사람이 읽는 설명을 담당한다.
- Redocly CLI로 lint와 문서 preview를 수행한다.
- `openapi-typescript`로 frontend request/response type을 생성한다.
- 생성 파일은 직접 수정하지 않는다.
- backend controller test는 OpenAPI example과 상태 코드를 기준으로 작성한다.
- CI는 OpenAPI validation과 생성 type drift를 검사한다.

Spring annotation에서 spec을 생성하는 code-first 방식은 backend 구현이 계약을 일방적으로 바꾸기 쉬워 채택하지 않는다. 전체 server stub 생성도 학습해야 할 controller 흐름을 가리고 generator 설정을 늘리므로 사용하지 않는다.

## 10. Daily Ranking Scheduler

- Spring `@Scheduled`를 사용한다.
- `Asia/Seoul` 기준 매일 00:00에 전날 날짜를 인자로 application service를 호출한다.
- scheduler는 먼저 `ranking_runs`에 대상 날짜를 원자적으로 claim하며 실패하거나 장시간 멈춘 run은 같은 application service로 재시도한다.
- claim한 instance만 전체 및 장르별 snapshot을 계산한다.
- 완료 전 snapshot은 공개하지 않고 `COMPLETED` 상태에서만 `FINAL`로 조회한다.
- 실패 run은 원인을 보존하고 동일 application service를 운영 명령이나 test에서 재실행할 수 있게 한다.
- 유휴 상태의 무료 application server가 예약 시각을 놓친 경우 최초 과거 차트 조회가 누락된 날짜를 동일한 claim 절차로 확정한다.

Quartz, Spring Batch, 별도 Worker와 Redis distributed lock은 MVP에 도입하지 않는다. Ranking 단계가 여러 job과 chunk restart를 요구할 정도로 커지면 Phase 13에서 Spring Batch를 검토한다.

## 11. 테스트 전략

### Backend

- 순수 unit test: domain policy와 normalization
- repository integration test: Testcontainers PostgreSQL 18
- API slice/integration test: Spring MockMvc 또는 RestTestClient
- provider adapter test: WireMock으로 Apple 성공, timeout, malformed response와 rate limit 검증
- concurrency integration test: 실제 PostgreSQL에서 quota 4회, 중복 Vote와 Track 동시 등록 검증
- scheduler test: 같은 날짜 재실행, 부분 실패와 completed 공개 조건 검증

[Spring Boot는 Testcontainers와 service connection을 공식 지원](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)한다. H2를 사용하지 않고 migration을 포함한 실제 PostgreSQL 동작을 검증한다.

### Frontend

- Vitest: formatter, state와 component unit test
- React Testing Library: 사용자 행동 중심 component test
- MSW: OpenAPI example 기반 API mock
- Playwright: 가입, 로그인, 검색, 추천, Vote, 오늘/과거 차트 핵심 흐름
- desktop/mobile screenshot과 접근성 기본 검사

## 12. Repository와 실행 구조

```text
TrackPick/
  backend/
    pom.xml
    mvnw
    src/
  frontend/
    package.json
    package-lock.json
    src/
  docs/
    00-project-baseline.md
    api/
    architecture/
    product/
  compose.yaml
  .github/workflows/ci.yml
  README.md
```

Local development:

1. Docker Compose로 PostgreSQL 18을 실행한다.
2. backend는 Maven Wrapper로 실행한다.
3. frontend는 Vite dev server를 실행하고 `/api`를 backend로 proxy한다.

Production:

1. frontend를 static assets로 build한다.
2. multi-stage Docker build에서 assets를 Spring Boot static resource에 포함한다.
3. 하나의 application container가 frontend와 `/api/v1`을 same-origin으로 제공한다.
4. PostgreSQL은 별도 managed 또는 persistent service를 사용한다.

배포 provider는 가격과 무료 정책이 자주 변하므로 Phase 14에서 결정한다. application package는 특정 provider에 종속되지 않는 Docker image로 만든다.

## 13. 주요 대안과 Trade-off

| 대안 | 장점 | 채택하지 않는 이유 |
| --- | --- | --- |
| Node.js/NestJS backend | frontend와 언어 통일 | Java 학습 목표와 Spring transaction/batch 경험을 얻지 못함 |
| Kotlin + Spring | 간결한 문법 | Java 기초와 Kotlin을 동시에 학습해야 함 |
| Spring WebFlux | 높은 동시 I/O 처리 | blocking JPA transaction과 함께 사용할 때 복잡도 증가 |
| MongoDB | schema 유연성 | quota, Vote, ranking 관계와 Unique Constraint에 부적합 |
| Redis session | 빠르고 확장에 익숙함 | MVP에 추가 인프라가 필요하고 PostgreSQL JDBC session으로 충분 |
| JWT | stateless API | same-origin web에서 revoke/logout/저장 보안 복잡도만 증가 |
| Next.js | SSR, full-stack 기능 | 별도 Node server와 Spring backend 책임 중복 |
| Tailwind/UI kit | 빠른 초기 조립 | 고유 UI와 CSS 학습을 줄이고 dependency가 늘어남 |
| Spring Batch 즉시 도입 | retry와 job metadata 기능 | 하루 한 번의 단일 snapshot job에는 과도함 |
| Microservices | 독립 배포 | 개인 MVP의 transaction과 운영 복잡도를 불필요하게 키움 |

## 14. 이후 구현에 미치는 영향

- Phase 6은 `backend`, `frontend`, Docker Compose와 CI를 한 repository에 생성한다.
- 최초 Flyway migration에 Genre, User, Track, Recommendation, Vote, quota, ranking과 session schema를 포함한다.
- API controller를 만들기 전에 Markdown REST 명세를 OpenAPI 3.1로 옮긴다.
- 인증 기능은 Spring Security와 JDBC session을 전제로 구현한다.
- ranking과 quota는 JPA convenience보다 SQL의 원자성과 window function을 우선한다.
- frontend는 generated API type을 사용하며 임의 response interface를 중복 정의하지 않는다.
- 배포 전 frontend build가 backend artifact에 포함되는지 production profile test로 검증한다.

## 15. 현재 개발 환경 점검

2026-08-30 최종 점검 결과:

| 도구 | 상태 | Phase 6 조치 |
| --- | --- | --- |
| Git | `2.53.0.windows.2` 확인 | 그대로 사용 |
| Java | Temurin `25.0.4.1 LTS` 설치 확인 | project target Java 25와 일치 |
| Maven | PATH에서 확인되지 않음 | repository에 Maven Wrapper를 포함하므로 전역 설치 불필요 |
| Node.js/npm | Node.js `24.20.0 LTS`, npm `11.19.0` 확인 | 그대로 사용 |
| Docker | Docker Desktop `4.88.1`, Engine `29.7.2`, Compose `5.4.0` 확인 | Linux engine 정상 연결 |

설치 여부는 명령이 PATH에서 확인되는지를 기준으로 판단했다. 이미 설치돼 있지만 PATH 연결만 빠진 경우에는 새로 설치하지 않고 기존 경로를 연결한다.

## 16. Phase 5에서 고정된 항목

1. Java 25 + Spring Boot 4.1 + Maven
2. PostgreSQL 18 + Flyway + JPA/JdbcClient 혼합
3. UUID v4와 ICU case-insensitive collation
4. Supabase Auth + Spring Security + Spring Session JDBC
5. React 19.2 + TypeScript + Vite 8 + Node 24 LTS
6. OpenAPI 3.1 contract-first
7. `@Scheduled` + DB claim 기반 Ranking batch
8. Testcontainers와 Playwright를 포함한 테스트 계층
9. 단일 Docker application image로 배포 가능한 모듈형 모놀리스

## 17. Phase 5 완료 조건

- Phase 6에서 추가 선택 없이 project scaffold를 만들 수 있다.
- DB, session, password, API contract와 scheduler 방식이 정해져 있다.
- Java 학습 목표와 구현 복잡도의 trade-off가 기록돼 있다.
- 외부 API rate limit과 preview 정책이 기술 구성에 반영돼 있다.
- test와 production이 개발용 대체 DB나 임시 schema 생성에 의존하지 않는다.
- 되돌리기 어려운 architecture 선택이 ADR에 기록돼 있다.
