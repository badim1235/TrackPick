# TrackPick MVP Project Baseline

> 상태: **Accepted**
>
> 최근 개정일: 2026-09-03
>
> 제품 정책의 단일 기준 문서다. 다른 문서와 제품 정책이 충돌하면 이 문서를 먼저 확인하고 관련 문서를 함께 갱신한다.

## 1. 제품 정의

TrackPick은 익명 계정 사용자들이 자신이 좋아하는 음악을 소개하고, 다른 사용자들이 하루에 제한된 표로 지지하여 전체 및 장르별 Daily Chart를 만드는 커뮤니티 기반 Music Discovery Platform이다.

핵심 질문은 다음과 같다.

> 오늘 다른 사람들이 가장 추천하고 싶은 음악은 무엇인가?

개인화 추천 알고리즘보다 사람의 한정된 일일 선택, 한줄평과 날짜별 순위를 중심으로 한다.

## 2. 확정된 제품 정책

| 영역 | 기준 정책 |
| --- | --- |
| 서비스명 | `TrackPick` |
| 계정 | Supabase Auth 기반 이메일·비밀번호 로그인을 사용한다. 비밀번호는 8~16자이며 공백 없이 영문자와 숫자를 각각 하나 이상 포함하고 특수문자는 선택이다. |
| 공개 Identity | 이메일은 공개하지 않는다. 검수된 앞 단어 50개, 뒤 명사 50개와 숫자 네 자리를 붙여 자동 생성한 고유 익명 닉네임만 노출하며 지정된 어색한 조합 6개는 제외한다. |
| 계정 복구 | 가입 시 이메일 확인을 요구하고 Supabase Auth의 이메일 기반 비밀번호 재설정을 사용한다. 이메일이 로그인 ID이므로 별도 ID 찾기는 제공하지 않는다. |
| 로그인 유지 | 여러 기기 동시 로그인을 허용한다. 일반 로그인은 session cookie를 사용하고, 사용자가 유지 옵션을 선택하면 마지막 인증 요청부터 7일 동안 유효한 영속 session cookie를 갱신한다. 로그아웃은 현재 session만 종료한다. |
| 가입·인증 제한 | 로그인과 가입 요청은 IP당 분당 30회로 제한한다. 동일 IP에서 한 시간 내 성공한 가입은 5개까지 허용하고 다음 가입 시도부터 24시간 차단한다. 로그인 실패 횟수로 계정을 잠그지는 않는다. |
| 일일 추천권 | 신규 Track 소개와 기존 Track Vote를 합산해 사용자당 하루 4회다. UI는 `오늘의 추천 n/4`로 표시한다. |
| 서비스 날짜 | `Asia/Seoul` 기준이며 저장 시각은 UTC instant를 사용한다. |
| 신규·재추천 | 외부 카탈로그에서 Track을 선택하고 1~120자 한줄평으로 Recommendation 회차를 생성한다. 장르는 Apple Music 원본 분류를 자동 적용한다. 생성자의 첫 Vote도 같은 트랜잭션에서 생성되고 추천권 1회를 소비한다. |
| 기존 곡 지지 | 오늘 차트에 등록된 Track에는 별도 Recommendation 없이 오늘의 Vote를 생성하며 추천권 1회를 소비한다. |
| 중복·재추천 | 같은 사용자는 같은 Track에 같은 날짜로 한 번만 Vote할 수 있다. 같은 Track의 새 Recommendation 회차는 마지막 등록일부터 3일이 지난 날부터 만들 수 있고, Track·서비스 날짜별 회차는 하나만 허용한다. |
| 홈 | 오늘 Vote가 많은 Track과 오늘 등록된 Track을 별도 섹션으로 표시한다. 날짜가 바뀌면 최근 등록 목록도 새 날짜 기준으로 초기화한다. |
| 장르 | Apple Music의 최상위 카탈로그 장르명을 기준으로 관리한다. 사용자가 장르를 선택하거나 수정하지 않으며 검색 곡의 Apple 원본 장르를 자동 적용한다. 매핑할 수 없는 provider 분류는 `Other`로 처리한다. |
| 차트 | 전체 및 장르별 Daily Chart를 제공하고 추천순 Top 20을 먼저 표시한 뒤 더 보기를 지원한다. |
| 순위 | `ROW_NUMBER`를 사용한다. Vote 수 내림차순, Track 이름의 대소문자를 구분하지 않는 오름차순, 아티스트명 오름차순, Track ID 오름차순으로 고유 순서를 정한다. |
| 오늘 차트 | 오늘의 Vote를 실시간 집계하는 `LIVE` 차트다. |
| 과거 차트 | 자정 배치로 전체·장르별 Top 50을 확정한 `DailyRanking` snapshot을 `FINAL` 읽기 전용 목록으로 제공한다. 화면은 20곡을 먼저 보여주고 더 보기로 나머지 30곡을 제공하며, Vote와 신규 추천은 제공하지 않는다. |
| 음악 검색 | 첫 provider adapter는 Apple iTunes Search API다. `KR` storefront를 우선 조회하고 빈 결과일 때 `US` storefront로 보완한다. Explicit 곡을 포함한 관련도순 상위 20곡을 표시하고 Explicit 여부를 명시한다. 내부 Track ID와 외부 provider ID를 분리하고 가능한 경우 ISRC를 보존한다. |
| 미리듣기 | Apple이 공식 제공한 30초 preview URL만 원본에서 스트리밍한다. 시작 위치는 provider가 선택하므로 인트로라고 보장하지 않는다. |
| 재생 금지 사항 | YouTube player/embed를 사용하지 않고 음원을 다운로드, 절단, 변환, 캐시 또는 재호스팅하지 않는다. |
| 외부 듣기 | Apple이 응답한 외부 Track 링크와 요구되는 Store attribution을 preview 가까이에 표시한다. |
| 한줄평 신고 | 인증 사용자는 다른 사용자의 한줄평을 신고할 수 있다. 자기 신고, 동일 한줄평 중복 신고와 차단 사용자 신고는 허용하지 않는다. 미처리 신고 3건에 도달하면 작성자를 `FLAGGED`로 전환한다. |
| 관리자 조치 | `ADMIN_EMAILS` 화이트리스트의 관리자만 신고 관리 화면과 API를 사용한다. `FLAGGED` 사용자를 검색해 신고 내역을 확인한 뒤 `NORMAL` 복귀 또는 `BAN` 처리한다. `BAN`은 로그인을 정지하고 기존 세션과 한줄평 노출을 회수한다. |

## 3. MVP 포함 범위

- 가입, 로그인, 로그아웃과 자동 생성 익명 닉네임
- Apple 음악 검색과 Track 선택·정규화
- Apple Music 자동 장르와 한줄평을 포함한 신규·재추천 Recommendation
- 기존 Track Vote, 일일 4회 제한과 당일 중복 방지
- 오늘 추천 상위와 최근 등록으로 구성된 홈
- 전체·장르별 오늘 차트와 과거 DailyRanking snapshot
- Apple 공식 30초 preview와 외부 Track 링크
- 반응형 웹 UI
- Ranking snapshot scheduler와 재실행 가능한 batch
- 한줄평 신고 UI/API와 관리자 전용 검토·제재 화면
- 핵심 동시성, 장애와 데이터 무결성 자동화 테스트

## 4. MVP 제외 범위

- 별도 사용자명 로그인과 ID 찾기
- Rising, Hidden Gems, 주간·월간·역대 차트
- 댓글, 팔로우, 플레이리스트와 개인화 추천
- Taste Profile, 추천 이력과 사용자 활동 통계
- 닉네임 변경, Vote 취소와 한줄평 수정·삭제
- 복수 음악 provider 동시 검색
- 모든 사용자에게 0초부터 시작하는 인트로 재생 보장
- Apple Music 구독자 인증을 사용하는 MusicKit 전체 곡 재생
- Redis 필수 도입과 별도 Batch/Worker 서비스

## 5. 핵심 불변식

1. Recommendation 생성, 최초 Vote 생성과 일일 quota 소비는 하나의 트랜잭션이다.
2. Vote 생성과 일일 quota 소비는 하나의 트랜잭션이다.
3. 사용자·Track·서비스 날짜 조합의 Vote는 하나만 존재한다.
4. 사용자·서비스 날짜별 성공한 Recommendation과 Vote 합계는 4회를 초과하지 않는다.
5. 외부 provider Track 하나는 내부에서 중복 생성되지 않는다.
6. Ranking의 원장은 Vote이며 누적 카운터를 정확성의 원천으로 사용하지 않는다.
7. Track의 Recommendation 회차는 서비스 날짜별 하나이며 마지막 회차일부터 3일이 지난 날부터 다시 생성할 수 있다.
8. 완료되지 않은 Ranking snapshot은 과거 확정 차트로 공개하지 않는다.
9. 공개 응답과 로그에 이메일, 비밀번호 또는 provider credential을 노출하지 않는다.
10. preview 부재나 외부 provider 장애가 기존 Track 조회, Vote와 과거 차트 조회를 막지 않는다.
11. 관리자 API는 화면 노출 여부와 무관하게 서버의 이메일 화이트리스트로 권한을 확인한다.

## 6. 분야별 상세 문서

- 화면과 행동: [`product/user-flows.md`](product/user-flows.md)
- 데이터 모델: [`architecture/erd.md`](architecture/erd.md)
- 기술 스택 제안: [`architecture/tech-stack.md`](architecture/tech-stack.md)
- REST API: [`api/rest-api.md`](api/rest-api.md)
- 기술 결정: [`architecture/adr/`](architecture/adr/)
- 초기 요구사항 이력: [`history/phase-01-mvp-requirements.md`](history/phase-01-mvp-requirements.md)

## 7. 확정된 기술 기준

Phase 5 기술 스택은 [`architecture/tech-stack.md`](architecture/tech-stack.md)에서 관리한다. Java 25, Spring Boot 4.1, PostgreSQL 18, React 19.2, Vite 8과 OpenAPI 3.1 기반 모듈형 모놀리스로 확정했다.

기술 선택의 배경과 trade-off는 [`architecture/adr/0002-modular-monolith-stack.md`](architecture/adr/0002-modular-monolith-stack.md)에 보존한다. 정확한 patch version과 transitive dependency는 Phase 6에서 생성하는 Maven과 npm lock에 고정한다. 배포 provider는 가격과 운영 조건을 확인해야 하므로 Phase 14에서 선택한다.

## 8. 변경 규칙

제품 정책을 바꿀 때는 이 문서를 먼저 수정하고 영향을 받는 user flow, ERD, API, ADR와 테스트를 같은 작업에서 갱신한다. 과거 Phase 문서는 결정 이력이므로 새로운 기준 정책을 추가하는 장소로 사용하지 않는다.
