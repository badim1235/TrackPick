# TrackPick 출시 전 체크리스트

> 상태: **In progress**
>
> 최근 개정일: 2026-09-08
>
> 코드로 확정할 수 있는 보강은 바로 진행하고, 제품 정책이나 운영자 정보가 필요한 항목만 결정 대상으로 남긴다. MVP 이후 사용자 피드백은 [`../product/post-mvp-backlog.md`](../product/post-mvp-backlog.md)에서 상세 관리한다.

## 1. 결정 없이 진행하는 필수 보강

| 항목 | 처리 내용 | 상태 |
| --- | --- | --- |
| 익명 session 누적 방지 | Spring Security request cache를 끄고 인증되지 않은 요청이 server session을 만들지 않게 한다. | 구현, 배포 전 검증 필요 |
| 잘못된 API 주소 | 등록되지 않은 API는 로그인 여부와 무관하게 JSON `404 NOT_FOUND`를 반환한다. | 구현, 배포 전 검증 필요 |
| 기존 익명 session 정리 | 인증 Security Context가 없는 Spring Session을 Flyway migration으로 삭제한다. | 구현, 배포 필요 |
| Supabase 최소 권한 | backend 전용 `public` 객체의 `anon`, `authenticated` 권한과 향후 자동 권한 부여를 회수한다. | 구현, 배포 필요 |
| 웹 보안 header | CSP, Referrer Policy와 Permissions Policy를 응답에 추가한다. | 구현, 배포 전 검증 필요 |
| 정적 자산 cache | hash가 붙은 `/assets/**`만 1년 immutable cache로 제공한다. HTML은 기존 no-cache를 유지한다. | 구현, 배포 전 검증 필요 |
| 운영 인증 redirect | 가입 확인과 비밀번호 복구 redirect를 `https://trackpick.net` 주소로 고정한다. | Render 설정 반영, 배포 필요 |
| 브랜드 metadata | health 응답, OpenAPI, Maven과 living document의 서비스명을 TrackPick으로 맞춘다. | 반영 |
| 활동 대시보드 | `나의 활동`에 추천 로그, 최초 등록, 회차별 Vote와 최고 반응 곡을 제공한다. | 구현, 배포 전 검증 필요 |
| 개인정보 자동 정리 | 가입 제한용 IP hash는 24시간 이내, 처리 완료 신고는 3개월 후 자동 삭제한다. | 구현, 배포 전 검증 필요 |
| 검색 언어 일관성 | 보완 검색의 후보도 `KR` lookup 메타데이터로 다시 확인해 검색과 등록 후 표기를 통일한다. | 구현, 외부 API 실동작 검증 필요 |
| 최신 한줄평 | 최초 한줄평은 보존하고 두 번째 회차부터 가장 최근 한줄평 하나를 추가 표시한다. | 구현, 배포 전 검증 필요 |

## 2. 확정했거나 별도 검토로 보류한 정책

### 2.1 동일 곡 판별 기준

- 동일한 Apple `externalTrackId`에는 기존 3일 재추천 제한을 적용한다.
- Apple이 서로 다른 `externalTrackId`로 제공하는 앨범판, 싱글판, 리마스터 등의 항목은 별도 Track으로 취급한다.
- 곡명·아티스트 정규화, 재생시간 fingerprint 또는 ISRC로 서로 다른 항목을 강제로 합치지 않는다.

### 2.2 개인정보 보관 정책

- 별도 가입 연령 제한과 생년월일 수집은 두지 않는다.
- 회원 탈퇴 시 계정 정보, 추천과 Vote를 삭제한다.
- 가입 제한용 IP hash는 24시간 이내, 처리 완료 신고 기록은 조치 후 3개월 보관한다.
- 이용약관 동의 화면은 현재 단계에서 생략한다.
- 공개 의견 제출은 분류, 본문과 접수 시각만 저장하며 계정, 이메일 또는 IP와 연결하지 않는다.
- 의견 화면에는 이름이나 이메일 등 개인정보를 적지 않도록 안내한다.

### 2.3 기존 운영 데이터 정리

- 최종 배포 직전에 차트, 추천과 Vote 데이터를 일괄 초기화한다.
- 개발 중인 테스트 데이터는 동작 확인에 사용하고 최종 초기화 대상에 포함한다.

## 3. 출시 전에 사용자가 직접 완료할 운영 작업

- Supabase database 비밀번호를 교체하고 Render의 `POSTGRES_PASSWORD`를 갱신한다.
- Supabase Auth의 유출 비밀번호 차단 기능을 활성화한다.
- Supabase Auth Site URL과 허용 Redirect URL이 모두 `https://trackpick.net` 기준인지 확인한다.
- Render의 `ADMIN_EMAILS`에 실제 관리자 이메일이 등록됐는지 확인하고 다시 로그인한다.
- 운영 DB의 첫 수동 backup을 만들고 복구 위치를 기록한다.
- 배포 후 UptimeRobot, 회원가입 메일, 로그인, 비밀번호 복구, 추천, Vote, 신고와 관리자 조치를 한 번씩 smoke test한다.

## 4. 사용자 피드백 결정 항목

| 기능 | 먼저 결정할 내용 | 권장 시작안 |
| --- | --- | --- |
| 닉네임 랜덤 재설정 | 변경 주기, 총 횟수, 과거 기록 표시명 | 30일에 한 번, 총 횟수 제한 없음, 모든 화면에 현재 닉네임 표시 |
| 내 추천 로그 | 구현 완료 | 본인만 조회, 최신순 20개, 곡·날짜·한줄평·회차별 Vote·최초 등록 표시 |
| 개인 대시보드 | 구현 완료 | 개인 기록 중심으로 추천 수, 최초 등록 수, 받은 Vote, 최고 반응 곡 제공 |
| 한줄평 수정 | 미도입 확정 | 작성 후 수정 불가. 재추천 시 새 회차 한줄평 작성 |
| 주간 데이터 기능 | 주간 경계, 집계 대상, 노출 수 | 월요일 00:00 KST 기준, 주간 Vote Top 20부터 시작 |

남은 피드백 기능은 출시를 막지 않는다. 다음 검토 대상은 `닉네임 재설정`과 `주간 데이터 기능`이다.

## 5. 최종 배포 검증

- backend 전체 테스트와 frontend `npm run check`가 통과한다.
- 알 수 없는 일반 주소와 API 주소가 각각 HTML 404와 JSON 404를 반환한다.
- 비로그인 보호 API는 `401`, 일반 사용자의 관리자 API는 `403`을 반환한다.
- 정적 hash 자산만 장기 cache되고 `index.html`은 즉시 갱신된다.
- 신규 익명 Spring Session이 생성되지 않고 기존 인증 session은 유지된다.
- Supabase Advisor의 새 security 또는 performance 경고가 없다.
