# TrackDrop REST API 명세

> 상태: **Proposed**
>
> 제품 정책 기준: [`../00-project-baseline.md`](../00-project-baseline.md)
>
> 화면 기준: [`../product/user-flows.md`](../product/user-flows.md)
>
> 데이터 기준: [`../architecture/erd.md`](../architecture/erd.md)
>
> 기원: Phase 4 설계 문서를 구현용 living document로 전환
>
> 작성일: 2026-08-30

## 1. 문서 목적

TrackDrop MVP의 화면과 도메인 명령을 HTTP API 계약으로 정의한다. 이 문서는 endpoint, 인증 경계, 요청·응답 schema, pagination, 오류 code, 동시성 충돌의 외부 표현을 정한다.

프레임워크 annotation, controller 클래스, ORM 구현은 Phase 5 기술 스택 결정 이후 작성한다. API 계약은 특정 서버 프레임워크에 의존하지 않는다.

## 2. 이 문서가 결정하는 사항

1. 공개 조회와 인증이 필요한 쓰기 API의 경계
2. 익명 공개 계정의 로그인 상태를 웹에서 어떻게 전달할지
3. Recommendation과 Vote를 어떤 endpoint로 분리할지
4. 오늘 live chart와 과거 final snapshot을 같은 API에서 어떻게 표현할지
5. `ROW_NUMBER` Top 20 더 보기를 안정적으로 제공할 방법
6. 일일 4회 소진, 중복 Vote, 동시 Track 등록을 어떤 오류로 반환할지
7. 외부 Music API 장애를 내부 API 오류와 어떻게 분리할지
8. 숨겨진 신고 기능을 비활성 상태에서 어떻게 보호할지

## 3. 추천 설계 요약

| 주제 | API 결정 |
| --- | --- |
| Base path | `/api/v1` |
| 형식 | JSON, field 이름은 `camelCase` |
| 인증 | `HttpOnly`, `Secure`, `SameSite=Lax` 세션 cookie |
| CSRF | cookie 인증의 모든 상태 변경 요청에 CSRF token 요구 |
| 공개 범위 | 홈, 장르, Track, 최근 등록, 오늘/과거 차트 조회 |
| 인증 범위 | 외부 음악 검색, Recommendation 생성, Vote, Account 조회, 신고 |
| Recommendation | `POST /recommendations`로 Track 최초 소개 또는 3일 후 재추천 회차 생성 |
| Vote | `POST /tracks/{trackId}/votes`로 오늘의 지지 생성 |
| 일일 권한 | 쓰기 성공 응답에 `limit`, `used`, `remaining`, `resetAt` 포함 |
| Chart | 오늘은 `LIVE`, 과거 완료 snapshot은 `FINAL`, batch 처리 중은 `PROCESSING` |
| Pagination | 기본 20개, 과거 차트의 두 번째 페이지는 30개, opaque cursor 사용 |
| Ranking | `ROW_NUMBER`: Vote 수 내림차순, Track 이름 오름차순, 아티스트명, Track ID |
| Music provider | MVP adapter는 Apple iTunes Search API이며 Apple 공식 30초 preview만 사용 |
| 오류 | 안정적인 domain error code와 사용자용 message, 추적용 `traceId` |
| 신고 | feature flag가 꺼진 환경에서는 route가 없는 것처럼 404 반환 |

## 4. 선택 이유와 Trade-off

### 4.1 세션 cookie 인증

TrackDrop은 같은 origin의 웹 frontend와 API를 우선 대상으로 한다. 인증 token을 JavaScript 저장소에 노출하지 않는 `HttpOnly` cookie가 XSS로 인한 token 탈취 위험을 줄이고 로그아웃과 세션 만료 정책도 이해하기 쉽다.

Trade-off:

- cookie 인증은 CSRF 방어가 필요하다.
- 서버 session 저장 방식이 필요하다.

MVP API는 cookie 계약만 확정한다. 실제 session이 DB, Redis, framework session 중 어디에 저장되는지는 Phase 5에서 선택하며, session 인프라 테이블은 도메인 ERD에 포함하지 않는다.

### 4.2 Recommendation과 Vote endpoint 분리

차트 밖의 곡을 처음 또는 다시 소개할 때는 provider metadata 재검증, Track 정규화, 대표 Genre와 한줄평 저장이 필요하다. 오늘 차트에 이미 올라온 곡의 Vote는 `trackId`만 필요하다. 두 행위의 입력과 실패 이유가 다르므로 endpoint를 분리한다.

### 4.3 live chart pagination은 as-of cursor 사용

오늘 차트는 Vote가 계속 들어오므로 첫 20곡과 다음 20곡 사이에 순위가 바뀔 수 있다. 첫 요청 시 서버가 `asOf`를 정하고 cursor에 포함한다. 이어지는 요청은 `created_at <= asOf`인 Vote만 사용해 같은 시점의 순위를 재계산한다.

장점:

- 더 보기 중 항목 중복과 누락을 줄인다.
- 별도 live snapshot 테이블이 필요 없다.

Trade-off:

- pagination 중 새 Vote가 바로 보이지 않는다.
- 사용자가 새로고침하면 cursor 없이 현재 시점 차트를 다시 받는다.

### 4.4 과거 차트의 읽기 전용 의미

과거 차트 응답에는 `actions.canVote=false`를 반환하고 화면에서도 Vote 행동을 노출하지 않는다. Vote endpoint 자체는 언제나 서버의 오늘 날짜에만 Vote를 생성하므로 과거 snapshot을 변경할 수 없다.

과거 차트 화면 자체에는 추천 진입점을 제공하지 않는다. Track 상세에서는 현재 차트 등록 여부와 곡 단위 3일 재추천 대기 기간을 확인하며, 차트 밖의 곡은 추천 화면에서 새 한줄평과 함께 재등록해야 한다.

## 5. 공통 규칙

### 5.1 URL과 version

- Base URL: `/api/v1`
- 리소스 이름은 복수형 kebab-case를 사용한다.
- API version은 URL에서 관리한다.
- 내부 Entity 클래스명이나 DB table 이름을 response에 노출하지 않는다.

### 5.2 시간과 날짜

- instant: ISO 8601 UTC, 예: `2026-08-30T12:34:56Z`
- business date: ISO 8601 date, 예: `2026-08-30`
- 오늘 날짜와 quota reset은 `Asia/Seoul` 기준으로 서버가 결정한다.
- 클라이언트는 Vote 날짜를 전송할 수 없다.

### 5.3 Identifier

- 모든 외부 `id`는 문자열로 직렬화한다.
- 클라이언트는 ID의 형식이나 정렬 가능성에 의존하지 않는다.
- provider의 external Track ID와 내부 `trackId`를 명확히 구분한다.

### 5.4 Content type

- 요청: `Content-Type: application/json`
- 응답: `application/json; charset=utf-8`
- body가 없는 성공 응답은 `204 No Content`

### 5.5 Pagination

- 목록의 기본 및 최대 page size는 MVP에서 20이다.
- 클라이언트는 server가 반환한 opaque `nextCursor`를 수정하거나 해석하지 않는다.
- `nextCursor=null`이면 더 불러올 항목이 없다.
- 잘못되거나 다른 query에 재사용된 cursor는 `INVALID_CURSOR`를 반환한다.

공통 page schema:

```json
{
  "page": {
    "size": 20,
    "hasMore": true,
    "nextCursor": "opaque-cursor"
  }
}
```

### 5.6 정렬

Daily Chart의 고유 순위 기준:

1. `voteCount DESC`
2. Track 이름 case-insensitive ascending
3. 아티스트명 case-insensitive ascending
4. `trackId ASC`

Track 이름과 아티스트명의 다국어 collation은 서버와 batch에서 반드시 같은 설정을 사용한다.

최근 등록 기준:

1. `recommendation.createdAt DESC`
2. `recommendationId DESC`

## 6. 인증과 보안 계약

### 6.1 Session cookie

권장 cookie 속성:

```text
trackdrop_session=<opaque-value>;
HttpOnly;
Secure;
SameSite=Lax;
Path=/
```

- 로그인과 회원가입 성공 시 session cookie를 발급한다.
- 로그아웃 시 서버 session을 무효화하고 cookie를 만료시킨다.
- 인증 실패는 `401 UNAUTHENTICATED`다.
- 정지 계정은 `403 ACCOUNT_SUSPENDED`다.

### 6.2 CSRF

- `POST`, `PUT`, `PATCH`, `DELETE` 요청은 유효한 CSRF token이 필요하다.
- frontend는 `GET /auth/csrf`에서 token을 받고 `X-CSRF-Token` header로 전송한다.
- token 누락 또는 불일치는 `403 CSRF_TOKEN_INVALID`다.
- `SameSite` cookie만을 유일한 CSRF 방어로 보지 않는다.

### 6.3 개인정보

- 공개 Track과 Recommendation 응답에는 공개 닉네임만 포함한다.
- 이메일은 공개 API에 포함하지 않고 본인의 Account API에서만 반환한다.
- 비밀번호, 비밀번호 hash, session, CSRF token은 로그에 기록하지 않는다.

### 6.4 입력 기본값

| 필드 | MVP 권장 검증 |
| --- | --- |
| `password` | 8~16자, 영문자·숫자 필수, 공백 불가 |
| `email` | RFC 호환 parser 사용, 최대 320자, Supabase Auth와 정규화 email 기준 Unique |
| `comment` | trim 후 1~120자 |
| music search `query` | trim 후 1~100자 |
| report `details` | 선택, 최대 500자 |

문자열 검증은 frontend와 server가 모두 수행하되 server가 최종 기준이다.

## 7. 공통 Response 모델

### 7.1 GenreSummary

```json
{
  "id": "genre-id",
  "code": "rock",
  "displayName": "Rock",
  "sortOrder": 30
}
```

### 7.2 QuotaSummary

`used`는 오늘 이미 사용한 횟수다. 화면은 `오늘의 추천 used/4`로 표시한다.

```json
{
  "date": "2026-08-30",
  "limit": 4,
  "used": 2,
  "remaining": 2,
  "resetAt": "2026-08-30T15:00:00Z"
}
```

### 7.3 TrackCard

```json
{
  "id": "track-id",
  "title": "Weird Fishes / Arpeggi",
  "artistName": "Radiohead",
  "albumName": "In Rainbows",
  "albumCoverUrl": "https://provider.example/cover.jpg",
  "primaryGenre": {
    "id": "genre-id",
    "code": "rock",
    "displayName": "Rock"
  },
  "recommendation": {
    "id": "recommendation-id",
    "comment": "후반부 기타가 들어올 때까지 꼭 들어보세요.",
    "commentAvailable": true,
    "recommenderNickname": "새벽리듬4881",
    "createdAt": "2026-08-30T10:00:00Z"
  },
  "todayVoteCount": 142,
  "viewer": {
    "hasVotedToday": false
  },
  "preview": {
    "available": true,
    "provider": "APPLE_MUSIC",
    "kind": "OFFICIAL_30_SECOND_CLIP",
    "startPosition": "PROVIDER_SELECTED",
    "url": "https://provider.example/preview.m4a"
  },
  "externalLinks": [
    {
      "provider": "APPLE_MUSIC",
      "url": "https://music.apple.com/..."
    }
  ]
}
```

미인증 응답에서 `viewer`는 null이다. 한줄평이 숨겨졌으면 `comment=null`, `commentAvailable=false`이며 원문을 반환하지 않는다.

### 7.4 ErrorResponse

```json
{
  "error": {
    "code": "ALREADY_VOTED",
    "message": "이미 오늘 추천한 곡입니다.",
    "fieldErrors": [],
    "details": {
      "trackId": "track-id"
    },
    "traceId": "trace-id"
  }
}
```

- `code`는 클라이언트 분기용 안정적인 값이다.
- `message`는 사용자에게 표시할 수 있지만 화면 문체에 맞게 frontend가 code를 번역할 수 있다.
- `details`에는 비밀정보와 provider 원본 오류를 넣지 않는다.
- 예상하지 못한 오류의 message에는 내부 예외 내용을 포함하지 않는다.

## 8. Endpoint 요약

| Method | Path | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/auth/csrf` | 공개 | CSRF token 발급 |
| GET | `/auth/login-id-availability` | 공개 | 가입 아이디 형식·중복 확인 |
| POST | `/auth/sign-up` | 공개 | 계정 생성 및 로그인 |
| POST | `/auth/login` | 공개 | 로그인 |
| POST | `/auth/logout` | 필요 | 로그아웃 |
| GET | `/me` | 필요 | 내 계정과 오늘 quota 조회 |
| DELETE | `/me` | 필요 | 비밀번호 재확인 후 계정과 활동 기록 삭제 |
| GET | `/genres` | 공개 | 활성 장르 목록 |
| GET | `/home` | 공개 | 오늘 추천 상위와 최근 등록 |
| GET | `/tracks/recent` | 공개 | 최근 등록 Track 목록 |
| GET | `/tracks/{trackId}` | 공개 | Track 상세 |
| GET | `/music/search` | 필요 | 외부 Music API 검색 |
| POST | `/recommendations` | 필요 | Track 최초 또는 재추천 회차 생성 |
| POST | `/tracks/{trackId}/votes` | 필요 | 기존 Track에 오늘 Vote |
| GET | `/charts/daily` | 공개 | 오늘 live 또는 과거 final 차트 |
| POST | `/recommendations/{recommendationId}/reports` | 필요 | 한줄평 신고 |
| GET | `/admin/reports/users` | 관리자 | `FLAGGED` 사용자 검색 |
| GET | `/admin/reports/users/{userId}` | 관리자 | 사용자별 신고 내역 조회 |
| PATCH | `/admin/reports/users/{userId}` | 관리자 | 무혐의 또는 이용 제한 조치 |

Ranking batch와 강제 재실행은 공개 REST API로 노출하지 않는다. scheduler와 권한이 제한된 운영 명령에서 동일 application service를 호출한다.

## 9. Auth API

### 9.1 GET /auth/csrf

Response `200 OK`:

```json
{
  "token": "csrf-token"
}
```

token 전달 방식은 선택한 security framework에 맞게 cookie + header 패턴으로 구현할 수 있으나 JavaScript가 session cookie를 읽을 수 있게 만들지는 않는다.

### 9.2 POST /auth/sign-up

- 공개 session에서도 CSRF token 필요

Request:

```json
{
  "email": "listener@example.com",
  "password": "chatgpt5555"
}
```

처리:

1. 이메일과 비밀번호 정책을 검증한다.
2. 가입 요청 제한을 확인한다.
3. Supabase Auth에 가입을 요청한다.
4. Supabase Auth가 확인 메일을 발송한다.
5. 이메일 확인 후 최초 로그인에서 TrackDrop 공개 닉네임과 profile을 생성한다.

Response `202 Accepted`:

```json
{
  "emailVerificationRequired": true
}
```

오류:

- `400 VALIDATION_FAILED`
- `429 RATE_LIMITED`
- `503 AUTH_PROVIDER_UNAVAILABLE`

### 9.3 POST /auth/login

- 공개 session에서도 CSRF token 필요

Request:

```json
{
  "email": "listener@example.com",
  "password": "chatgpt5555",
  "rememberMe": true
}
```

Response `200 OK`: account/quota 구조를 반환하고 TrackDrop session cookie를 발급한다.

`rememberMe=false`는 브라우저 종료 시 제거되는 session cookie를 사용한다. `true`이면 마지막 인증 요청부터 7일 동안 유효한 persistent session cookie를 사용하며 인증 요청마다 만료를 갱신한다. 여러 기기 session을 허용하고 logout은 현재 session만 무효화한다.

입력과 제한 정책:

- 가입 password는 8~16자이고 공백 없이 영문자와 숫자를 각각 하나 이상 포함한다. 특수문자는 선택이다.
- login, signup과 비밀번호 복구 요청을 합산해 IP당 분당 30회로 제한한다.
- 동일 IP에서 한 시간 내 성공한 가입은 5개까지 허용하고 다음 가입 시도부터 24시간 차단한다.

오류:

- `401 INVALID_CREDENTIALS`: 이메일 존재 여부, 미확인 상태와 비밀번호 오류를 구분하지 않는다.
- `403 ACCOUNT_SUSPENDED`
- `429 RATE_LIMITED`

### 9.4 POST /auth/password-recovery

- 공개 session에서도 CSRF token 필요
- Supabase Auth에 이메일 기반 비밀번호 재설정 메일 발송을 요청한다.
- 계정 존재 여부를 응답에서 구분하지 않는다.
- Response `202 Accepted`

Supabase 복구 링크가 `/recover/password`로 돌아오면 frontend는 URL fragment의 짧은 수명 access token을 읽어 새 비밀번호와 함께 `POST /auth/password-reset`으로 전달한다. backend는 token을 로그나 DB에 저장하지 않고 Supabase Auth의 사용자 갱신 API에 전달한다. 성공 응답은 `204 No Content`다. 현재 비밀번호와 같은 값은 `422 PASSWORD_UNCHANGED`, 만료되었거나 올바르지 않은 복구 token은 `401 PASSWORD_RECOVERY_INVALID`로 구분한다.

### 9.5 POST /auth/logout

- 인증 및 CSRF 필요
- server session 무효화
- Response `204 No Content`

동일 session에서 중복 제출돼도 server 오류를 만들지 않는다.

### 9.6 GET /me

Response `200 OK`:

```json
{
  "account": {
    "email": "listener@example.com",
    "publicNickname": "새벽리듬4881",
    "emailVerified": true,
    "createdAt": "2026-08-30T12:00:00Z"
  },
  "quota": {
    "date": "2026-08-30",
    "limit": 4,
    "used": 2,
    "remaining": 2,
    "resetAt": "2026-08-30T15:00:00Z"
  }
}
```

### 9.7 DELETE /me

- 인증 및 CSRF 필요
- 현재 비밀번호로 Supabase Auth 재인증
- 사용자의 추천·투표·추천권·신고 기록과 해당 추천에 종속된 과거 차트 항목 삭제
- 다른 추천이 남지 않은 곡의 내부 catalog row 삭제
- TrackPick의 모든 기기 session과 Supabase Auth 사용자 삭제
- Response `204 No Content`

Request:

```json
{
  "password": "chatgpt5555"
}
```

오류:

- `400 VALIDATION_FAILED`
- `401 INVALID_CREDENTIALS`
- `503 ACCOUNT_DELETION_UNAVAILABLE`

이메일은 로그인 ID이므로 별도 ID 찾기 API를 제공하지 않는다. 이메일 확인과 비밀번호 재설정 token은 Supabase Auth가 관리한다.

## 10. Genre API

### GET /genres

- 공개
- `active=true`, `sortOrder ASC` 기준
- `ALL`은 반환하지 않는다.

Response `200 OK`:

```json
{
  "items": [
    { "id": "genre-1", "code": "alternative", "displayName": "Alternative", "sortOrder": 10 },
    { "id": "genre-2", "code": "hip-hop-rap", "displayName": "Hip-Hop/Rap", "sortOrder": 110 },
    { "id": "genre-3", "code": "k-pop", "displayName": "K-Pop", "sortOrder": 130 }
  ]
}
```

Recommendation 화면은 사용자의 장르 선택을 받지 않는다. 서버가 Apple Music의 `primaryGenreName`을 활성 시스템 장르에 매칭하며, 일치하지 않으면 `Other`를 사용한다.

## 11. Home API

### GET /home

- 공개
- 인증 cookie가 있으면 viewer Vote 상태와 quota를 함께 반환
- cursor pagination 대신 각 섹션의 대표 항목과 전체보기 URL 제공

Response `200 OK`:

```json
{
  "asOf": "2026-08-30T12:30:00Z",
  "quota": null,
  "trending": {
    "title": "오늘 가장 많이 추천받는 노래",
    "items": [],
    "viewAllPath": "/chart"
  },
  "recent": {
    "title": "최근 등록된 노래",
    "items": [],
    "viewAllPath": "/recent"
  }
}
```

`items`는 TrackCard 목록이며 각 섹션은 최대 6곡을 반환한다. 두 섹션 중 하나의 조회가 실패하면 전체 endpoint를 부분 성공으로 만들지 않고 server 내부에서 재시도·관측한다. 최종 실패 시 명확한 오류를 반환한다.

## 12. Track API

### 12.1 GET /tracks/recent

Query:

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `cursor` | N | 이전 응답의 opaque cursor |

Response `200 OK`:

```json
{
  "asOf": "2026-08-30T12:30:00Z",
  "items": [],
  "page": {
    "size": 20,
    "hasMore": false,
    "nextCursor": null
  },
  "quota": null
}
```

정렬은 Recommendation 생성 시각 내림차순이며 순위 번호는 없다.

### 12.2 GET /tracks/{trackId}

- 공개
- 인증 cookie가 있으면 `viewer.hasVotedToday`와 quota 포함

Response `200 OK`:

```json
{
  "track": {},
  "today": {
    "voteCount": 142,
    "overallRank": 3,
    "genreRank": 1,
    "asOf": "2026-08-30T12:30:00Z"
  },
  "quota": null,
  "actions": {
    "canVote": false,
    "canRecommend": false,
    "reason": "UNAUTHENTICATED",
    "recommendationAvailableOn": "2026-09-02"
  }
}
```

`track`은 TrackCard와 같은 핵심 필드에 보조 장르와 provider reference를 추가한 상세 schema다.

오류:

- `404 TRACK_NOT_FOUND`

## 13. External Music Search API

### GET /music/search

- 인증 필요
- 내부 DB의 Track 검색이 아니라 Apple iTunes Search API 음악 검색 proxy
- MVP 응답의 provider는 항상 `APPLE_MUSIC`

Query:

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `query` | Y | 곡명, 아티스트 또는 조합, 1~100자 |

MVP는 `country=KR`, `media=music`, `entity=song`, `limit=20`, `explicit=Yes`로 우선 검색한다. KR 응답이 비어 있으면 동일 조건의 `country=US` 검색으로 보완한다. Apple이 반환한 관련도 순서를 유지하며 별도 pagination은 제공하지 않는다.

Response `200 OK`:

```json
{
  "provider": "APPLE_MUSIC",
  "storefront": "KR",
  "attribution": "Music preview provided courtesy of iTunes",
  "items": [
    {
      "provider": "APPLE_MUSIC",
      "externalTrackId": "external-id",
      "title": "Weird Fishes / Arpeggi",
      "artistName": "Radiohead",
      "albumName": "In Rainbows",
      "albumCoverUrl": "https://provider.example/cover.jpg",
      "releaseYear": 2007,
      "isrc": null,
      "explicit": true,
      "preview": {
        "available": true,
        "provider": "APPLE_MUSIC",
        "kind": "OFFICIAL_30_SECOND_CLIP",
        "startPosition": "PROVIDER_SELECTED",
        "url": "https://provider.example/preview.m4a"
      },
      "externalUrl": "https://music.apple.com/kr/album/example/external-id",
      "existingTrack": {
        "registered": false,
        "trackId": null,
        "inCurrentChart": false,
        "hasVotedToday": false,
        "recommendationAvailableOn": null,
        "action": "SELECT"
      }
    }
  ]
}
```

검색 결과는 후보일 뿐 신뢰 가능한 쓰기 payload가 아니다. Recommendation 제출 시 server가 `provider + externalTrackId`로 metadata를 다시 검증한다.

`existingTrack.action`은 다음 네 상태 중 하나다.

- `SELECT`: 최초 등록이거나 마지막 등록일부터 3일이 지나 새 한줄평과 함께 등록할 수 있다.
- `VOTE`: 오늘 차트에 등록됐고 현재 사용자는 아직 추천하지 않았다.
- `VOTED`: 현재 사용자가 오늘 이미 추천했다.
- `WAIT`: 오늘 차트에는 없지만 3일 대기 기간이 끝나지 않았다. `recommendationAvailableOn`을 함께 표시한다.

`explicit=true`인 결과도 숨기지 않고 UI에서 `Explicit`으로 표시한다. Apple iTunes Search API는 ISRC를 항상 제공하지 않으므로 `isrc`는 nullable이다.

`startPosition=PROVIDER_SELECTED`는 preview가 곡의 0초부터 시작한다고 보장하지 않는다는 뜻이다. server와 client는 URL을 다운로드하거나 잘라서 인트로 파일을 만들지 않으며, YouTube URL 또는 video ID를 preview 대체값으로 반환하지 않는다.

오류:

- `400 SEARCH_QUERY_INVALID`
- `401 UNAUTHENTICATED`
- `429 RATE_LIMITED`
- `503 MUSIC_PROVIDER_UNAVAILABLE` with `retryable=true`

provider의 원본 오류 message와 credential은 반환하지 않는다.

## 14. Recommendation API

### POST /recommendations

- 인증 및 CSRF 필요
- Track 최초 소개 또는 재추천 회차 생성, Apple Music Genre 자동 분류, 한줄평 저장
- 추천 회차의 최초 Vote와 quota 1회 소비를 하나의 transaction으로 처리

Request:

```json
{
  "provider": "APPLE_MUSIC",
  "externalTrackId": "external-id",
  "comment": "후반부 기타가 들어올 때까지 꼭 들어보세요."
}
```

검증:

- provider는 MVP에서 `APPLE_MUSIC`이어야 하며 externalTrackId가 실제 Track을 가리켜야 한다.
- server가 Apple Music의 `primaryGenreName`을 활성 시스템 Genre와 매칭한다.
- 일치하는 활성 Genre가 없으면 `Other`로 분류하며, 사용자의 Genre 선택이나 자유 입력은 허용하지 않는다.
- comment는 trim 후 1~120자다.
- 사용자의 오늘 quota가 남아 있어야 한다.
- 같은 곡은 마지막 `recommendedOn`의 KST 날짜에서 3일이 지난 날부터 다시 등록할 수 있다. 9월 1일 등록이면 9월 4일부터 가능하다.
- 오늘 차트에 이미 Vote가 있는 곡은 새 회차를 만들지 않고 Vote endpoint를 사용한다.

Response `201 Created`:

```json
{
  "track": {},
  "recommendation": {
    "id": "recommendation-id",
    "primaryGenre": {
      "id": "genre-id",
      "code": "rock",
      "displayName": "Rock"
    },
    "comment": "후반부 기타가 들어올 때까지 꼭 들어보세요.",
    "createdAt": "2026-08-30T12:40:00Z"
  },
  "vote": {
    "created": true,
    "votedOn": "2026-08-30"
  },
  "quota": {
    "date": "2026-08-30",
    "limit": 4,
    "used": 3,
    "remaining": 1,
    "resetAt": "2026-08-30T15:00:00Z"
  }
}
```

오류:

- `400 VALIDATION_FAILED`
- `400 PROVIDER_GENRE_UNAVAILABLE`
- `401 UNAUTHENTICATED`
- `409 RECOMMENDATION_COOLDOWN`
- `409 ALREADY_IN_CURRENT_CHART`
- `429 DAILY_LIMIT_EXCEEDED`
- `503 MUSIC_PROVIDER_UNAVAILABLE`

재추천 대기 response `409 Conflict`:

```json
{
  "error": {
    "code": "RECOMMENDATION_COOLDOWN",
    "message": "최근 추천된 곡입니다. 9월 4일부터 다시 추천할 수 있습니다.",
    "details": {
      "existingTrackId": "track-id",
      "existingRecommendationId": "recommendation-id",
      "recommendationAvailableOn": "2026-09-04",
      "quotaConsumed": false
    },
    "traceId": "trace-id"
  }
}
```

대표 Genre 수정 endpoint는 MVP에 포함하지 않는다. ERD는 향후 통제된 변경 가능성을 열어두지만 변경 기능을 추가할 때 live chart 재분류 정책을 먼저 정의해야 한다.

## 15. Vote API

### POST /tracks/{trackId}/votes

- 인증 및 CSRF 필요
- body 없음
- 서버의 오늘 날짜로만 Vote 생성
- 오늘 차트에 이미 등록된 Track에만 직접 Vote할 수 있다.
- 차트 밖의 Track은 대기 중이면 `RECOMMENDATION_COOLDOWN`, 대기가 끝났으면 `RECOMMENDATION_REQUIRED`를 반환해 한줄평 없는 우회 등록을 막는다.

Response `201 Created`:

```json
{
  "vote": {
    "trackId": "track-id",
    "votedOn": "2026-08-30",
    "createdAt": "2026-08-30T12:45:00Z"
  },
  "todayVoteCount": 143,
  "quota": {
    "date": "2026-08-30",
    "limit": 4,
    "used": 4,
    "remaining": 0,
    "resetAt": "2026-08-30T15:00:00Z"
  }
}
```

오류:

- `401 UNAUTHENTICATED`
- `404 TRACK_NOT_FOUND`
- `409 ALREADY_VOTED`
- `409 RECOMMENDATION_COOLDOWN`
- `409 RECOMMENDATION_REQUIRED`
- `429 DAILY_LIMIT_EXCEEDED`

Duplicate retry가 Unique Constraint에 걸리면 quota 증가 transaction도 rollback한다. `ALREADY_VOTED` details에는 현재 `trackId`, `votedOn`, quota를 포함해 frontend가 성공 상태처럼 동기화할 수 있게 한다.

Vote 취소 endpoint는 제공하지 않는다.

## 16. Daily Chart API

### GET /charts/daily

Query:

| 이름 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `date` | N | 오늘 | `Asia/Seoul` business date |
| `genre` | N | `all` | `all` 또는 Genre code |
| `cursor` | N | 없음 | 이전 응답의 opaque cursor |

미래 날짜는 요청할 수 없다.

Response `200 OK`:

```json
{
  "date": "2026-08-30",
  "status": "LIVE",
  "scope": {
    "type": "GENRE",
    "genre": {
      "id": "genre-id",
      "code": "rock",
      "displayName": "Rock"
    }
  },
  "asOf": "2026-08-30T12:50:00Z",
  "items": [
    {
      "rank": 1,
      "voteCount": 142,
      "track": {}
    }
  ],
  "page": {
    "size": 20,
    "hasMore": true,
    "nextCursor": "opaque-cursor"
  },
  "quota": null,
  "actions": {
    "canVote": true
  }
}
```

정렬과 rank:

```text
ROW_NUMBER(
  voteCount DESC,
  normalizedTrackTitle ASC,
  normalizedArtistName ASC,
  trackId ASC
)
```

오늘 차트:

- `status=LIVE`
- 첫 page에서 `asOf` 생성
- 후속 cursor에 `asOf`, scope, 마지막 rank를 안전하게 포함
- 인증 사용자는 각 Track의 `hasVotedToday`와 quota를 받음

과거 차트:

- 완료 snapshot이면 `status=FINAL`
- 전체·장르별 Top 50만 저장하고 `daily_rankings.rank` 순서 사용
- 첫 응답은 20곡, 더 보기 응답은 나머지 30곡
- `actions.canVote=false`
- Vote와 Recommendation URL을 제공하지 않음

과거 batch 처리 중 Response `202 Accepted`:

```json
{
  "date": "2026-08-29",
  "status": "PROCESSING",
  "retryAfterSeconds": 30,
  "items": []
}
```

오류:

- `400 FUTURE_DATE_NOT_ALLOWED`
- `400 GENRE_NOT_FOUND`
- `400 INVALID_CURSOR`
- `404 RANKING_NOT_AVAILABLE`: 처리 중도 아니고 snapshot도 없는 비정상 상태

완료된 과거 차트에 곡이 없으면 오류가 아니라 `200 FINAL`과 빈 items를 반환한다.

## 17. Report API

### POST /recommendations/{recommendationId}/reports

Request:

```json
{
  "reasonCode": "ABUSIVE_LANGUAGE",
  "details": "추가 설명"
}
```

Response `201 Created`:

```json
{
  "report": {
    "id": "report-id",
    "status": "PENDING",
    "createdAt": "2026-08-30T13:00:00Z"
  }
}
```

정책:

- 동일 사용자의 동일 Recommendation 중복 신고 불가
- 자신의 Recommendation 신고 불가
- 이미 `BAN` 처리된 사용자의 Recommendation 신고 불가
- 신고 생성만으로 한줄평 자동 숨김 없음
- 작성자의 미처리 신고가 `REPORT_LIMIT`(기본 3)에 도달하면 `FLAGGED`로 전환

오류:

- `401 UNAUTHENTICATED`
- `403 SELF_REPORT_NOT_ALLOWED`
- `404 RECOMMENDATION_NOT_FOUND`
- `409 ALREADY_REPORTED`
- `409 REPORTED_USER_BANNED`

### Admin report API

- `GET /admin/reports/users?query=`는 `FLAGGED` 사용자만 닉네임·이메일로 검색한다.
- `GET /admin/reports/users/{userId}`는 해당 사용자의 신고 사유, 설명, 신고자와 한줄평을 반환한다.
- `PATCH /admin/reports/users/{userId}`에 `{"action":"DISMISS"}`를 보내면 대기 신고를 종결하고 사용자를 `NORMAL`로 되돌린다.
- `PATCH /admin/reports/users/{userId}`에 `{"action":"BAN"}`을 보내면 계정을 `SUSPENDED`로 전환하고 세션과 모든 한줄평 노출을 회수한다.
- 관리자 API는 로그인 여부와 별도로 `ADMIN_EMAILS` 서버 환경변수의 이메일 화이트리스트를 검사한다. 비관리자에게는 `404 NOT_FOUND`를 반환한다.

## 18. HTTP Status와 Error code

| HTTP | code | 의미 |
| ---: | --- | --- |
| 400 | `VALIDATION_FAILED` | field 검증 실패 |
| 400 | `INVALID_CURSOR` | cursor 위변조 또는 query 불일치 |
| 400 | `FUTURE_DATE_NOT_ALLOWED` | 미래 차트 요청 |
| 400 | `PROVIDER_GENRE_UNAVAILABLE` | provider 장르를 시스템 Genre로 분류할 수 없음 |
| 401 | `UNAUTHENTICATED` | 로그인 필요 |
| 401 | `INVALID_CREDENTIALS` | 로그인 실패 |
| 403 | `CSRF_TOKEN_INVALID` | CSRF 검증 실패 |
| 403 | `ACCOUNT_SUSPENDED` | 정지 계정 |
| 403 | `SELF_REPORT_NOT_ALLOWED` | 자신의 한줄평 신고 |
| 404 | `TRACK_NOT_FOUND` | Track 없음 |
| 404 | `RECOMMENDATION_NOT_FOUND` | Recommendation 없음 |
| 404 | `RANKING_NOT_AVAILABLE` | 과거 snapshot 비정상 누락 |
| 404 | `NOT_FOUND` | 존재하지 않거나 feature flag로 비활성화된 route |
| 409 | `RECOMMENDATION_COOLDOWN` | 곡 단위 3일 재추천 대기 중 |
| 409 | `ALREADY_IN_CURRENT_CHART` | 다른 사용자가 먼저 오늘 차트에 등록 |
| 409 | `ALREADY_VOTED` | 오늘 동일 Track 중복 Vote |
| 409 | `RECOMMENDATION_REQUIRED` | 차트 밖의 곡에 새 한줄평 등록 필요 |
| 409 | `ALREADY_REPORTED` | 동일 한줄평 중복 신고 |
| 409 | `REPORTED_USER_BANNED` | 이미 이용 제한된 사용자 신고 |
| 409 | `USER_NOT_FLAGGED` | 현재 관리자 조치 대상이 아닌 사용자 |
| 429 | `DAILY_LIMIT_EXCEEDED` | 오늘의 추천 4회 소진 |
| 429 | `RATE_LIMITED` | 보안 또는 provider 보호용 요청 제한 |
| 503 | `MUSIC_PROVIDER_UNAVAILABLE` | 외부 Music API 장애 또는 timeout |
| 503 | `AUTH_PROVIDER_UNAVAILABLE` | Supabase Auth 장애 또는 timeout |
| 500 | `INTERNAL_ERROR` | 예상하지 못한 내부 오류 |

`DAILY_LIMIT_EXCEEDED`에는 `quota`와 `resetAt`을, `RATE_LIMITED`에는 가능한 경우 `Retry-After` header를 제공한다.

## 19. 권한 Matrix

| 기능 | 비회원 | 인증 사용자 | 관리자 | 정지 사용자 |
| --- | ---: | ---: | ---: | ---: |
| 홈/Track/장르 조회 | O | O | O | O |
| 오늘/과거 차트 조회 | O | O | O | O |
| Preview/외부 링크 | O | O | O | O |
| 외부 Music 검색 | X | O | O | X |
| Recommendation 생성 | X | O | O | X |
| 오늘 Vote | X | O | O | X |
| 과거 차트에서 Vote | X | X | X | X |
| Account 조회 | X | O | O | 제한된 상태만 허용 가능 |
| 한줄평 신고 | X | O | O | X |
| 신고 검토와 제재 | X | X | O | X |

## 20. 동시성과 Retry 계약

### Vote

- 동일 Vote 동시 요청 중 하나만 `201`이다.
- 나머지는 `409 ALREADY_VOTED`이며 quota는 추가 소비되지 않는다.
- network timeout 후 재요청이 `ALREADY_VOTED`면 frontend는 server state를 조회해 완료 상태로 동기화한다.

### Recommendation

- 같은 provider Track의 동시 추천 회차 생성 중 하나만 `201`이다.
- 나머지는 `409 RECOMMENDATION_COOLDOWN` 또는 `409 ALREADY_IN_CURRENT_CHART`와 기존 내부 Track ID를 받는다.
- 충돌한 요청의 quota는 소비되지 않는다.

### Daily limit

- 같은 User의 동시 요청이 남은 quota보다 많으면 최대 남은 횟수만 성공한다.
- 실패 요청은 `429 DAILY_LIMIT_EXCEEDED`와 최신 quota를 받는다.

MVP에서는 별도 Idempotency-Key 저장 테이블을 도입하지 않는다. DB Unique Constraint와 transaction rollback으로 중복 데이터와 quota 오소비를 방지한다.

## 21. 외부 Music API 장애 계약

- 첫 provider adapter는 Apple iTunes Search API다. Apple이 제공하는 공식 30초 preview URL과 외부 Track URL만 내부 schema로 변환한다.
- 외부 provider 호출에는 연결/응답 timeout을 적용한다.
- 제한된 횟수의 짧은 retry만 허용하고 사용자 요청을 오래 점유하지 않는다.
- provider 장애는 기존 Track 조회, Vote, 차트 조회에 영향을 주지 않는다.
- 검색과 신규 Recommendation metadata 재검증만 `503 MUSIC_PROVIDER_UNAVAILABLE`이 될 수 있다.
- provider 오류 본문, credential, 내부 stack trace를 response나 일반 로그에 남기지 않는다.
- 오류 details의 `retryable`과 `retryAfterSeconds`는 server가 판단 가능한 경우만 제공한다.

## 22. Cache 계약

Redis는 Phase 1 MVP 필수 요소가 아니다. cache를 추가하더라도 다음 계약을 지킨다.

- cache는 응답 성능 최적화이며 정확성의 원장이 아니다.
- Vote 성공 후 오늘 chart/home cache는 무효화하거나 짧은 TTL로 갱신한다.
- 과거 `FINAL` snapshot은 날짜·scope별 장기 cache가 가능하다.
- 사용자별 `hasVotedToday`와 quota를 공유 공개 cache에 섞지 않는다.
- cache 장애 시 관계형 DB 조회로 fallback한다.

## 23. 관측 가능성

모든 요청은 `traceId`를 가진다.

구조화 로그와 metric 후보:

- endpoint, status, latency, traceId
- 로그인 성공/실패 횟수(비밀번호와 이메일 원문 제외)
- Recommendation/Vote 성공 및 domain conflict 수
- `DAILY_LIMIT_EXCEEDED` 수
- provider별 검색 latency, timeout, error rate
- chart query latency와 반환 item 수
- Ranking batch 상태는 REST 요청과 별도 job metric으로 기록
- 신고 endpoint feature flag 거부 수는 민감정보 없이 기록

## 24. 구현 및 테스트 영향

필수 contract test:

1. 회원가입 요청이 Supabase Auth로 전달되고 이메일 확인을 요구함
2. 공개 응답에서 이메일 미노출
3. CSRF 누락 쓰기 요청 거부
4. Recommendation 생성 시 최초 Vote와 quota 동시 반영
5. 동일 Track 동시 등록의 단일 성공
6. 동일 Track 당일 중복 Vote의 단일 성공
7. 동시 요청에서도 하루 4회 초과 불가
8. `ROW_NUMBER` 동일 득표수 Track 이름 정렬
9. Track 이름이 같을 때 아티스트명과 Track ID tie-break
10. live chart cursor의 `asOf` 고정
11. 과거 chart의 `FINAL` 읽기 전용 계약
12. batch 처리 중 `PROCESSING` 응답
13. provider 장애가 기존 Vote/차트에 전파되지 않음
14. 신고 feature flag off에서 404
15. 한줄평 숨김 시 원문 미노출
16. preview 응답이 Apple 공식 URL과 `PROVIDER_SELECTED` 시작 위치 계약을 지킴
17. YouTube URL과 video ID가 검색·Track 응답에 포함되지 않음

## 25. 구현 전에 확정할 기술 선택

1. Backend/frontend framework와 언어
2. 관계형 DB 제품과 Unicode case-insensitive collation
3. PK 물리 타입
4. server session 저장 방식
5. Supabase Auth와 application session의 책임 경계
6. Apple iTunes Search API adapter의 storefront, 호출 제한, Store attribution 적용 방식
7. API 문서화 도구와 OpenAPI 생성 방식
8. scheduler 실행 및 중복 방지 방식

## 26. 문서 완료 조건

- 모든 MVP 화면의 조회와 명령이 endpoint에 연결된다.
- 공개/인증/feature flag 권한 경계가 명확하다.
- Recommendation과 Vote의 transaction 결과가 HTTP 응답으로 표현된다.
- 일일 4회와 중복 충돌에 안정적인 error code가 있다.
- `ROW_NUMBER` Track 이름 정렬과 Top 20 cursor가 정의된다.
- 오늘 live와 과거 final 차트의 의미가 구분된다.
- 외부 Music API 장애가 내부 API에 어떻게 전달되는지 정의된다.
- 구현 기술 선택에 필요한 미결정 사항이 분리된다.
