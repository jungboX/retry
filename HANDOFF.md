# 작업 인계: 청도 일손이음 MVP

## 목적과 기준 상태

자연어 농가 요청부터 조건 확인, 후보 추천, 농가 승인, 구직자 수락까지의 MVP 흐름을 구현하고 문서 요구사항과 일치시킨다. 이 문서는 2026-11-05 기준으로 네 명의 담당 영역, 브랜치·PR 절차, 배포 경로를 정한다.

먼저 `AGENTS.md`와 제품 문서(`IDEATION.md`, `PRD.md`, `TDD.md`, `TRD.md`)를 읽는다. 실제 개인정보와 비밀값을 추가하지 않고, 매칭은 양측 승인 후에만 확정한다.

- 기준 브랜치는 `main`이다. 이전 인계 브랜치 `feat/mvp-matching-flow`는 PR #1로 `main`에 머지됐으며 더 이상 쓰지 않는다.
- 현재 코드는 통합 테스트와 Azure 배포가 완료되지 않은 상태다. 로컬 빌드 성공을 Azure 배포 성공의 증거로 보고하지 않는다.

## 확정된 결정

| 주제 | 결정 | 근거 |
|---|---|---|
| 브랜치 | 담당자별 브랜치를 `main`에서 만들고 PR로 머지한다 | 이 문서 |
| 배포 경로 | Aspire(`aspire deploy`) → Azure Container Apps. Bicep + Static Web Apps 경로는 폐기한다 | TRD §9·§10, TDD ADR-008 |
| 웹 배포 | 웹 컨테이너가 정적 파일을 서빙하고 `/api`를 API로 프록시한다 (CORS 불필요) | TRD §9 |
| 문서 소유 | 제품·기술 문서(`PRD`, `TDD`, `TRD`, `AGENTS`, `README`)는 담당자 4가 소유한다. 다른 담당자는 PR로 변경을 제안한다 | 이 문서 |

## 네 명의 기능별 담당

| 담당 | 책임 영역 | 소유 경로 | 완료 조건 |
|---|---|---|---|
| 1. API·데이터 | 세션 저장, 작업·매칭 API, 승인 상태 | `services/api/**`, `shared/openapi.yaml` | DB 재시작 후 복구, 세션 분리, 누락 조건 차단, 양측 승인 상태 테스트 통과, OpenAPI와 코드 일치 |
| 2. Agent·MCP | 자연어 구조화, 순차 워크플로, 도구·점수 | `services/agent/**`, `services/mcp-server/**` | 실제 도구 이름·인자·응답 연결, 점수 규칙이 PRD PR-03과 일치, 안전 도구, Agent/MCP 테스트 통과 |
| 3. 프런트엔드 | 농가·구직자 핵심 여정과 상태 표시 | `frontend/**` | 타입 검사·프로덕션 빌드 통과, 로딩·오류·누락 조건·승인·새로고침 흐름 확인 |
| 4. Aspire·Azure·문서 | 배포, 서비스 연결, CI, E2E, 제품·기술 문서 | `aspire/**`, `infra/**`, `.github/workflows/**`, 루트 문서(`*.md`), `.env.example` | 아래 담당자 4 작업 목록의 항목을 실제 실행 결과로 확인 |

소유 경로 밖의 파일은 수정하지 않는다. 필요하면 소유자에게 변경을 요청한다. Dockerfile은 각 서비스 경로(`services/*/Dockerfile`)이므로 해당 서비스 소유자가 소유한다.

### 담당자 4 작업 목록 (Aspire 배포)

1. (완료) AppHost를 Aspire 13.6.4, .NET 10 SDK(`net10.0`)로 올리고 빌드했다. .NET 10 SDK와 Aspire CLI(`dotnet tool install -g Aspire.Cli`)가 필요하다.
2. (완료) AppHost에 Azure Container Apps 환경(`aca-env`)을 추가했다. `aspire publish`로 Bicep 생성을 확인했고 외부 ingress는 게이트웨이뿐이다.
3. (완료) API·Agent·MCP를 `AddDockerfile` 컨테이너 리소스로 바꿨다. 빌드 컨텍스트는 저장소 루트다. 로컬에서 세 이미지 모두 빌드된다.
4. (모델 완료, 실배포 미검증) YARP 게이트웨이가 정적 파일을 서빙하고 `/api`를 API로 프록시한다. 외부 공개 엔드포인트는 게이트웨이뿐이다. 이 게시 API는 공식 문서에서 실험적이므로 첫 실배포에서 검증한다.
5. (모델 완료, TLS·네트워크 미검증) PostgreSQL을 로컬은 컨테이너, Azure는 관리형 Flexible Server로 모델링했다. 배포 후 TLS·네트워크 접근 범위를 검증한다.
6. (완료) Copilot 토큰을 비밀 매개변수 `copilotGithubToken`으로 선언하고 Agent에 `COPILOT_GITHUB_TOKEN`으로 주입한다.
7. (부분 완료) `aspire publish`로 Bicep 생성까지 확인했다. 실제 `aspire deploy`는 Azure 자격 증명이 없어 미검증이며, 배포가 끝났다고 보고하지 않는다.
8. (작성 완료, 첫 실행 미검증) `.github/workflows/ci.yml`에 프런트엔드 빌드, Agent `pytest`, `mvn test`(api·mcp), AppHost 빌드를 추가했다.
9. Aspire 배포가 검증된 뒤 `infra/main.bicep` 처리를 결정한다. 그 전에는 삭제하지 않고, 이 파일로 배포했다고 보고하지 않는다.

다른 담당자에게 요청할 변경:

- 담당자 3: 요청 없음. 로컬 개발은 Vite 개발 서버(5173)가 `localhost:8081`로 프록시하고, AppHost가 API 컨테이너를 호스트 8081에 고정 노출한다. 이 값을 바꾸면 AppHost도 같이 바꾼다.
- 담당자 1: 관리형 Azure PostgreSQL TLS는 PR #2로 반영됐다(`DataSourceConfiguration`). 배포 후 실제 연결만 확인한다.
- 담당자 1·2: 각 Dockerfile이 Aspire 컨테이너 리소스에서 빌드되는지 확인한다.
- 담당자 1: 완료. PR #2로 Azure PostgreSQL TLS가 반영됐고 `mvn test`(16개)가 통과한다.
- (완료) `services/mcp-server/pom.xml`에 `spring-boot-starter-test`를 추가했다. 깨끗한 빌드에서 테스트 3개가 통과하고 Docker 이미지가 빌드된다.

## 배포 인수인계 (배포 담당자용)

Azure 계정을 가진 사람이 배포한다. 아래 전제가 모두 충족된 뒤에 시작한다.

**전제 조건**

- 세 이미지(`services/*/Dockerfile`)가 저장소 루트 컨텍스트에서 `docker build`로 성공하는지 배포 직전에 다시 확인한다.
- Azure 구독(크레딧 확인), 예산 알림 설정.
- 설치: Azure CLI(`az`), Docker(실행 중), .NET 10 SDK, Aspire CLI.
- 배포 실행자가 발급한 Copilot 토큰. 저장소·채팅·제출물에 넣지 않는다. `aspire deploy` 프롬프트나 `aspire secret`에 터미널에서 직접 입력한다.

**실행**: 명령은 TRD §10을 따른다. 리전은 배포자가 정하되 Postgres Flexible Server를 지원하는 지역이어야 한다(미지원이면 다른 리전).

**배포 후 확인 항목** (확인 결과를 이 문서에 기록한다)

1. 게이트웨이 URL(`*.azurecontainerapps.io`)에서 프런트엔드가 뜨고 `/api/demo/state`가 응답하는지.
2. API, Agent, MCP의 외부 주소가 닫혀 있는지 (외부 ingress는 게이트웨이뿐).
3. API가 관리형 Postgres에 TLS로 연결되고 재시작 후에도 데이터가 보존되는지, Postgres 네트워크 접근 범위.
4. Agent가 컨테이너에서 Copilot 토큰으로 비대화형 호출에 성공하는지 (한 번 `/api/jobs/analyze` 호출).
5. 농가 요청부터 `CONFIRMED`까지 한 흐름이 완료되는지.

**정리**: 심사가 끝나면 `az group delete`로 리소스 그룹을 삭제한다(삭제 전 대상 확인). 비용 추정은 앱 4개가 `minReplicas: 1`인 상시 가동이므로 무료 사용량을 넘을 수 있다. 배포 전 Azure 가격 계산기로 확인한다.

## 브랜치·PR 절차

1. 시작 전에 `git switch main && git pull`로 최신 `main`을 받는다.
2. 자기 영역 브랜치를 `main`에서 만든다.
   - `feat/api-<주제>`, `feat/agent-<주제>`, `feat/frontend-<주제>`, `feat/aspire-<주제>`
   - 문서만 바꾸면 `docs/<주제>`
3. 소유 경로의 변경만 커밋한다. 커밋 전에 `git status --short`와 `git diff --check`로 다른 영역의 변경이 섞이지 않았는지 확인한다.
4. 커밋 제목은 `feat(api): ...`, `fix(frontend): ...`, `docs: ...` 형식을 따른다.
5. 자기 브랜치를 원격에 push하고 `main` 대상 PR을 만든다. PR 본문에는 영향받는 경로, 실행한 테스트와 결과, 못 돌린 검증을 적는다.
6. 리뷰어는 다음과 같다.
   - 계약(`openapi.yaml`, API→Agent, MCP 도구)이 바뀌면 소비 측 담당자
   - 그 외에는 담당자 4
7. `main`에 직접 push하지 않는다. 강제 push하지 않는다. `main`이 앞서 있으면 `git fetch origin` 후 `origin/main`을 자기 브랜치에 병합하고 충돌을 해결한 뒤 push한다.

각자 자기 PC에서 자기 브랜치를 작업하므로 커밋과 push는 서로 기다리지 않고 병렬로 한다. 같은 브랜치를 두 사람이 쓰지 않는다.

## 서비스 간 계약과 변경 절차

| 경계 | 계약 | 소유 |
|---|---|---|
| Web → API | `shared/openapi.yaml`, 모든 요청에 `X-Demo-Session-Id` 헤더 | 담당자 1 |
| API → Agent | `POST /api/jobs/analyze` (TRD §3) | 담당자 2 |
| Agent → MCP | 4개 MCP 도구의 이름·인자·응답 | 담당자 2 |

계약을 바꾸는 사람은 소유자가 먼저 PR을 올리고 머지한 뒤에만 소비 측이 따라간다. 점수 가중치를 바꿀 때는 코드, 테스트, `PRD.md` PR-03을 같은 PR에서 함께 바꾼다.

## 병렬 작업 규칙

| 항목 | 규칙 |
|---|---|
| 담당자 1·2·3 개발 | 병렬 진행. 소유 경로가 겹치지 않는다 |
| 같은 파일 | 동시에 수정하지 않는다 |
| 담당자 4 인프라 작업 | 담당자 1~3과 병렬로 시작할 수 있다 (AppHost 업그레이드, ACA 환경, CI) |
| 담당자 4 통합·E2E | 담당자 1~3의 PR이 머지된 뒤에 수행한다 |
| 문서 변경 | 담당자 4가 영향 범위(상위→하위 문서)를 확인한다 |

## 현재 변경 내용

- 프런트엔드는 요청 분석, 결과 확인, 농가 승인, 구직자 수락, 상태 조회를 API에 연결했다.
- API는 브라우저 데모 세션별 작업과 승인 상태를 관리하고 H2/PostgreSQL 저장을 추가했다.
- Agent는 Copilot SDK 분석과 Microsoft Agent Framework 순차 흐름을 구성하고, MCP 서버의 후보·점수·안전 도구를 호출한다.
- MCP 점수 계산과 테스트가 있다.
- 이 변경은 모두 `main`에 머지됐으나 통합 검증은 끝나지 않았다.

## 검증 현황 및 명령

2026-11-05 `feat/aspire-deploy` 기준 확인 결과: 프런트엔드 `npm ci`·프로덕션 빌드, AppHost `dotnet build`, `aspire publish`(Bicep 생성, 외부 ingress는 게이트웨이뿐)는 통과했다. **api `mvn test` 16개, mcp-server `mvn clean test` 3개가 통과한다.** Docker 이미지 빌드: agent, api, mcp-server 모두 성공했다. Agent 테스트(`uv`가 이 PC에 없어 미실행), E2E, `aspire deploy`는 확인하지 않았다. 점수 가중치 중 작업 종류 25점은 코드로 확인하지 않았다. 프런트엔드 의존성 설치 시 npm이 취약점 4건(중간 3, 높음 1)을 보고했으며, 범위 검토 없이 자동 업그레이드하지 않는다.

```bash
# 프런트엔드
npm --prefix frontend run build

# Python Agent (uv 필요)
(cd services/agent && uv run pytest -q)

# Java 서비스 (Java 21과 Maven 필요)
mvn -f services/api/pom.xml test
mvn -f services/mcp-server/pom.xml test

# Aspire (.NET SDK 필요, 담당자 4가 SDK 버전 확정 후)
dotnet build aspire/AppHost/AppHost.csproj

# 최종 변경 검사
git diff --check
git status --short
```

Windows에서는 `npm.cmd run build`, `Set-Location`을 사용한다. 이 개발 환경에는 Java 21과 .NET SDK가 확인되지 않았다. 각 담당자는 자기 영역의 테스트 환경을 먼저 갖추고 결과를 PR에 기록한다.

## 보안·배포 주의

- 실제 사람의 정보나 실제 비밀값을 저장소에 넣지 않는다. 배포 값은 `aspire secret` 또는 CI 비밀 저장소로만 전달한다.
- 배포 후 관리형 PostgreSQL의 네트워크 접근 범위를 점검한다. 공개 서비스 전에 경계를 검토한다.
- 웹만 외부에 노출하고 API·Agent·MCP는 내부 접근이어야 한다(TRD TR-09).
- Aspire 배포를 검증하기 전에 `infra/main.bicep`을 배포 경로로 사용하지 않는다. 두 경로를 혼합하지 않는다.
- 공개 데모는 로그인 없이 쓸 수 있어야 하므로, 실제 업무 데이터나 민감한 작업을 노출하지 않는다.
