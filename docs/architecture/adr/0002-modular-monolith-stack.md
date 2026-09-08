# ADR-0002. Modular Monolith Application Stack

> 상태: **Accepted**
>
> 결정일: 2026-08-30

## Context

TrackPick은 인증, 외부 음악 검색, Recommendation/Vote transaction, 일일 quota와 Ranking batch를 포함한다. 사용자에게는 Java 학습 목표와 PostgreSQL 경험이 있으며, 프로젝트는 개인 포트폴리오 규모다.

Backend와 batch를 분리하거나 microservice를 도입하면 transaction, 배포와 관측 지점이 늘어난다. 반대로 모든 코드를 기술 계층별 package에 모으면 feature 경계가 흐려지고 이후 변경 범위가 커진다.

## Decision

1. 하나의 repository와 하나의 Spring Boot application을 사용하는 모듈형 모놀리스로 구현한다.
2. Backend는 Java 25 LTS, Spring Boot 4.1.x와 Maven Wrapper를 사용한다.
3. PostgreSQL 18을 유일한 application database로 사용하고 Flyway가 schema를 관리한다.
4. Frontend는 React 19.2, TypeScript와 Vite 8 SPA로 만들고 production build를 Spring Boot artifact에 포함한다.
5. Backend package는 Identity, Catalog, Recommendation, Ranking, Moderation feature 기준으로 나눈다.
6. 외부 HTTP API, session, scheduler와 ranking은 같은 process 안에서 adapter/module 경계를 유지한다.
7. 별도 microservice, Redis, message broker와 worker는 MVP에 도입하지 않는다.

## Alternatives

| 대안 | 판단 |
| --- | --- |
| TypeScript/NestJS 단일 언어 | 생산성은 높지만 Java/Spring 학습 목표와 맞지 않음 |
| Kotlin/Spring | 간결하지만 Java와 Kotlin을 동시에 학습해야 함 |
| Next.js full-stack | Spring backend와 server 책임이 중복됨 |
| Backend, ranking worker 분리 | 하루 한 번의 MVP job에 독립 배포 비용이 큼 |
| Microservices | 분산 transaction과 운영 복잡도가 포트폴리오 규모를 초과함 |

## Consequences

- 하나의 DB transaction으로 quota, Vote와 Recommendation 무결성을 설명할 수 있다.
- Local과 production 실행 단위가 단순하다.
- module 경계는 network가 아니라 package와 test로 지켜야 한다.
- application instance를 늘릴 때 session과 scheduler가 안전하도록 JDBC session과 DB job claim을 사용해야 한다.
- 특정 module의 부하와 배포 주기가 실제로 달라질 때만 별도 service로 추출한다.

## References

- [Java Downloads and LTS releases](https://www.oracle.com/java/technologies/downloads/)
- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [PostgreSQL Versioning Policy](https://www.postgresql.org/support/versioning/)
- [React Versions](https://react.dev/versions)
- [Vite 8 Announcement](https://vite.dev/blog/announcing-vite8)
- [Node.js Releases](https://nodejs.org/en/about/previous-releases)
