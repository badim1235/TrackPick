# TrackPick

TrackPick은 사용자가 좋아하는 곡을 장르별로 소개하고, 하루 네 번의 추천권으로 오늘의 차트를 함께 만드는 음악 발견 서비스입니다.

## 기술 구성

- Backend: Java 25, Spring Boot 4.1, Spring Security, JPA, JdbcClient
- Frontend: React 19, TypeScript, Vite 8, TanStack Query
- Identity: Supabase Auth 이메일 인증
- Database: Supabase PostgreSQL, Flyway (로컬 개발은 PostgreSQL 18)
- Contract: OpenAPI 3.1

## 현재 구현 상태

Phase 13까지 구현되어 계정 인증부터 Apple 음악 검색, 곡 추천, 오늘의 실시간 차트와 과거 확정 차트까지 사용할 수 있습니다.

- 이메일과 비밀번호를 사용하는 Supabase 회원가입·로그인
- 가입 확인 메일과 비밀번호 재설정 메일 요청
- 고유한 한국어 익명 닉네임 자동 생성
- PostgreSQL server session 기반 로그인·로그아웃과 CSRF 보호
- Supabase `public` 함수의 `anon`·`authenticated` 실행 권한과 향후 자동 부여 차단
- 여러 기기 동시 로그인과 선택형 7일 로그인 유지
- 내 계정 이메일과 오늘의 추천권 조회
- 비밀번호 재확인 후 추천·투표 기록과 모든 로그인 세션을 함께 삭제하는 회원 탈퇴
- IP별 인증 요청 및 계정 생성 제한
- Apple KR 스토어 우선 검색과 빈 결과 시 US 보완 검색, 관련도순 상위 20곡 표시
- Explicit 곡 포함·표시, 앨범 정보와 Apple Music 외부 링크 제공
- 검색 메타데이터 캐시, Apple 호출 제한과 외부 서비스 오류 처리
- Apple Music 최상위 카탈로그 장르 목록과 곡별 원본 장르 자동 분류
- Apple Music 장르와 1~120자 한줄평을 포함한 최초·재추천 등록
- 등록과 동시에 해당 추천 회차의 최초 Vote 생성 및 오늘의 추천권 1회 원자적 차감
- 곡 단위 3일 재추천 대기와 하루 최대 4회 동시성 보호
- 검색 결과의 `선택`, `추천`, `추천 완료`, `추천 대기` 상태와 재추천 가능일 안내
- Track별 당일 중복 Vote 방지와 한도 초과 시 Vote·추천권 원자적 rollback
- 오늘 전체·장르별 실시간 차트와 `ROW_NUMBER` 고유 순위
- 득표수·곡명·아티스트·Track ID 기준 결정적 정렬
- 추천순 20곡과 고정 시점 cursor 기반 더 보기
- 차트에서 기존 Track 추천 및 추천권·순위 즉시 갱신
- 차트 곡별 Apple 30초 미리듣기와 외부 전체 듣기 링크
- 매일 00:00 KST 전날 전체·장르별 Top 50을 확정하는 재실행 가능 Ranking snapshot
- 날짜별 `FINAL` 과거 차트 조회와 읽기 전용 20곡 + 30곡 더 보기
- 유휴 서버가 자정 작업을 놓친 경우 최초 과거 조회에서 누락 snapshot 자동 복구
- 홈의 오늘 추천 상위 6곡과 오늘 최근 등록 6곡 실시간 표시
- 오늘 최근 등록 전체 목록과 고정 시점 cursor 기반 더 보기
- 홈·최근 목록의 미리듣기, Apple 링크와 추천권 연동
- 홈·차트·검색 결과에서 이어지는 공개 곡 상세 화면
- 곡 상세의 오늘 득표수, 전체·장르 순위, 한줄평, 미리듣기와 추천권 연동
- 동일 사용자 중복·자기 신고를 막는 한줄평 신고 API와 저장 구조 (`REPORTS_ENABLED=false`로 기본 숨김)

## 로컬 실행

필수 도구는 Java 25, Node.js 24, Docker Desktop과 Supabase 프로젝트입니다. Supabase Dashboard의 Email provider에서 `Confirm email`을 켜고 URL Configuration에 `http://127.0.0.1:5173/**`를 Redirect URL로 추가합니다.

```powershell
docker compose up -d postgres

cd backend
$env:SUPABASE_URL="https://your-project-ref.supabase.co"
$env:SUPABASE_PUBLISHABLE_KEY="sb_publishable_your_key"
$env:SUPABASE_SECRET_KEY="sb_secret_your_server_only_key"
.\mvnw.cmd spring-boot:run

cd ..\frontend
npm install
npm run dev
```

프런트엔드는 `http://localhost:5173`, API는 `http://localhost:8080`에서 실행됩니다. Vite 개발 서버가 `/api` 요청을 백엔드로 전달합니다.

프로젝트 루트의 `.env`에 Supabase Auth 설정이 준비되어 있다면 `.\scripts\run-local.ps1`로 로컬 PostgreSQL에 연결한 백엔드를 실행할 수 있습니다. Supabase PostgreSQL 연결 자체를 확인할 때만 `.\scripts\run-local.ps1 -UseConfiguredDatabase`를 사용합니다.

운영 환경에서는 Spring datasource를 Supabase PostgreSQL 연결 정보로 설정하고 `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY`, `AUTH_EMAIL_REDIRECT_URL`, `AUTH_PASSWORD_RECOVERY_REDIRECT_URL`, `IP_HASH_SECRET`, `SESSION_COOKIE_SECURE=true`를 별도로 설정합니다. `SUPABASE_SECRET_KEY`는 회원 탈퇴 시 Auth 사용자를 삭제하는 서버 전용 키이므로 프런트엔드나 저장소에 노출하면 안 됩니다. 실제 이메일 발송에는 Supabase Custom SMTP 설정을 권장합니다. Docker Desktop이 실행 중이어야 로컬 PostgreSQL과 Testcontainers 기반 통합 테스트를 사용할 수 있습니다.

한줄평 신고 기능은 저장 구조와 API만 준비되어 있으며 기본값은 비활성입니다. 운영 정책과 검토 절차를 마련한 뒤에만 `REPORTS_ENABLED=true`로 켜며, 현재 프런트엔드에는 신고 진입점을 노출하지 않습니다.

## Render 배포

루트의 `render.yaml`은 싱가포르 리전의 무료 Docker Web Service 하나에 프런트엔드와 백엔드를 함께 배포합니다. Render Blueprint 생성 화면에서 저장소를 연결하고 다음 값만 입력합니다.

- `DATABASE_URL`: Supabase Session pooler의 JDBC URL (`?sslmode=require` 포함)
- `POSTGRES_USER`: Supabase Session pooler 사용자
- `POSTGRES_PASSWORD`: Supabase 데이터베이스 비밀번호
- `SUPABASE_URL`: Supabase 프로젝트 URL
- `SUPABASE_PUBLISHABLE_KEY`: Supabase publishable key
- `SUPABASE_SECRET_KEY`: Supabase 서버 전용 secret key (`sb_secret_...`)

Render가 제공하는 `RENDER_EXTERNAL_URL`을 가입 확인과 비밀번호 재설정의 기본 리디렉션 주소로 사용합니다. 배포가 완료되면 Supabase Authentication의 URL Configuration에서 Site URL을 Render의 HTTPS 주소로 설정하고 다음 Redirect URL을 추가합니다.

```text
https://<render-host>/login?verified=1
https://<render-host>/recover/password
```

로컬 개발을 계속 사용하려면 기존 `http://127.0.0.1:5173/**` Redirect URL도 유지합니다. Render는 HTTPS 인증서를 자동으로 관리하며 운영 쿠키에는 `Secure` 속성이 적용됩니다.

### Render 상태 확인

운영 환경에서는 UptimeRobot의 Keyword Monitor가 5분 간격으로 `https://trackpick.net/actuator/health`를 조회합니다. 응답 본문의 `"status":"UP"` 키워드가 없으면 장애로 판단하도록 설정합니다.

이 모니터는 실제 GET 요청으로 Render 무료 Web Service의 유휴 종료를 줄이고 장애 알림을 제공합니다. 다만 무료 외부 모니터링은 가용성을 보장하지 않으므로, 상시 실행 보장이 필요하면 Render 유료 instance를 사용합니다.

## 검증

```powershell
cd backend
.\mvnw.cmd verify

cd ..\frontend
npm run check
```

설계 기준과 단계별 결정은 [`docs/README.md`](docs/README.md)에서 확인할 수 있습니다.

## 라이선스

[MIT](LICENSE)
