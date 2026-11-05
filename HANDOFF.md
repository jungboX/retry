# 작업 인계: 청도 일손이음 MVP

## 목적과 진행 기준

이 저장소의 현재 작업은 자연어 농가 요청부터 조건 확인, 후보 추천, 농가 승인, 구직자 수락까지의 MVP 흐름을 구현하고 문서의 요구사항과 일치시키는 것이다. 이번 인계 문서는 2026-11-05 기준의 작업 상태와 네 명의 담당 영역, 검증·커밋 순서를 정한다.

저장소의 `AGENTS.md`와 제품 문서(`IDEATION.md`, `PRD.md`, `TDD.md`, `TRD.md`)를 먼저 읽는다. 사용자 개인정보·자격 증명을 추가하지 않고, 매칭은 양측 승인 후에만 확정한다. 현재 코드는 작업 중이며, 통합 테스트와 Azure 배포가 완료된 상태가 아니다.

## Aspire와 Dockerfile에 관한 확인

- 초기 `TRD.md`는 로컬 런타임에 Aspire를, 클라우드에 Azure Container Apps와 Azure Static Web Apps를 기재했다. 하지만 Aspire로 배포하는 구체적인 구성이나 Dockerfile 없이 배포된다는 결정은 문서에 없었다.
- 초기 `aspire/AppHost/Program.cs`는 `AddNpmApp`과 `AddExecutable`로 서비스를 로컬 실행할 뿐, Azure 배포 타깃을 선언하지 않았다.
- 현재 변경된 AppHost도 로컬 실행·PostgreSQL 연결을 설정하지만, Azure 배포 타깃은 아직 없다. 따라서 현재 상태에서 `aspire deploy`가 Azure 리소스를 배포한다고 볼 수 없다. Aspire 공식 배포 문서는 배포 타깃 또는 배포 단계를 제공하는 리소스가 없으면 `deploy`가 수행할 작업이 없다고 설명한다: [Aspire deployment](https://aspire.dev/deployment/deploy-with-aspire/).
- API·Agent·MCP Dockerfile은 이전 구현 과정에서 추가됐지만, 현재 AppHost가 이를 참조하지 않는다. 그러므로 지금은 **배포에 필요한 것으로 확정되지 않은 미연결 파일**이다. Aspire의 Azure 배포 타깃을 정한 뒤, 각 서비스 리소스가 그 타깃에서 지원되는지 검증하고 유지·수정·삭제를 결정한다.
- 기존 `infra/main.bicep`은 컨테이너 이미지 주소를 받아 ACA를 구성하는 별도 배포 경로다. 이 경로를 사용할 경우 이미지 빌드·게시 단계가 필요하다. 이는 Aspire의 `aspire deploy` 경로와 동일하지 않으므로, 두 경로를 혼합하거나 배포 완료로 표시하지 않는다.

**담당자 4가 우선 결정할 사항:** 목표가 `aspire deploy`인지, Bicep 기반 Azure 배포인지 정하고 하나의 경로를 문서와 코드에서 일치시킨다. Dockerfile은 이 결정과 타깃 호환성 검증 뒤에 처리한다.

## 네 명의 기능별 담당

| 담당 | 책임 영역 | 소유 경로 | 작업 및 완료 조건 |
|---|---|---|---|
| 1. API·데이터 | 세션 저장, 작업·매칭 API, 승인 상태 | `services/api/**`, `shared/openapi.yaml` | DB 재시작 후 복구, 세션 분리, 누락 조건 차단, 양측 승인 상태를 테스트하고 OpenAPI 응답과 코드 일치 확인 |
| 2. Agent·MCP | 자연어 구조화, 순차 워크플로, 도구 및 점수 | `services/agent/**`, `services/mcp-server/**` | 실제 도구 이름·인자·응답 연결, 점수 규칙 일치, 안전 도구, Agent/MCP 테스트 통과 |
| 3. 프런트엔드 | 농가·구직자 핵심 여정과 상태 표시 | `frontend/**` | 타입 검사와 프로덕션 빌드 통과, 로딩·오류·누락 조건·승인·새로고침 흐름 확인 |
| 4. Aspire·Azure 통합 | 배포 경로 선택, 서비스 연결, 배포 및 E2E | `aspire/**`, `infra/**`, `TRD.md` 배포 절 | Aspire 배포 타깃 또는 Bicep 경로 중 하나를 정하고, 필요한 이미지/리소스/웹 배포 절차를 검증. 로컬 통합 및 가능한 Azure 사전 검증 수행 |

문서 전체의 요구사항 변경은 담당자 4가 영향 범위를 조정한다. 한 번에 같은 파일을 두 담당자가 수정하지 않는다. 담당자 4는 실제 실행 결과를 확인한 후에만 완료로 표시한다.

## 현재 변경 내용

- 프런트엔드는 요청 분석, 결과 확인, 농가 승인, 구직자 수락, 상태 조회를 API에 연결했다.
- API는 브라우저 데모 세션별 작업과 승인 상태를 관리하고 H2/PostgreSQL 저장을 추가했다.
- Agent는 Copilot SDK 분석과 Microsoft Agent Framework 순차 흐름을 구성하고, MCP 서버의 후보·점수·안전 도구를 호출하도록 변경했다.
- MCP 점수와 테스트를 추가했다.
- 문서, OpenAPI, Aspire AppHost, Azure Bicep, 서비스 Dockerfile이 함께 변경됐다.

모든 변경은 아직 통합 검증이 필요한 작업본이다. 팀원이 이어받을 기능 브랜치는 `feat/mvp-matching-flow`다. 담당자별 기능 커밋과 필요한 문서 커밋으로 정리한 뒤 이 브랜치를 원격에 push한다. 커밋 전에는 `git status --short`와 `git diff --check`로 다른 담당자의 미완료 변경을 확인한다.

### 원격에 push한 기준 커밋

2026-11-05에 `origin/feat/mvp-matching-flow`로 push했다. 팀원은 저장소에서 다음 명령으로 가져온다.

```powershell
git fetch origin
git switch --track origin/feat/mvp-matching-flow
```

브랜치를 이미 체크아웃했다면 `git pull`로 최신 변경을 받는다.

| 커밋 | 영역 |
|---|---|
| `037613e` | API, 세션 저장, OpenAPI |
| `ecea022` | Agent와 MCP |
| `4e6d4a3` | 프런트엔드 |
| `320d537` | Aspire와 Azure 리소스 |
| `d227868` | 제품 문서와 인계 문서 |

## 검증 현황 및 명령

프런트엔드 프로덕션 빌드와 Python 구문 검사는 통과했다. Agent 테스트, Java API/MCP 테스트, Aspire 빌드, Bicep 검증, E2E는 완료로 확인되지 않았다. 프런트엔드 의존성 설치는 실행됐으며 npm이 4건의 취약점(중간 3, 높음 1)을 보고했다. 의존성 자동 업그레이드는 범위 검토 없이 적용하지 않는다.

```powershell
# 프런트엔드
Set-Location frontend
npm.cmd run build

# Python Agent
Set-Location services\agent
python -m pytest -q

# Java 서비스: Java 21과 Maven이 준비된 환경에서 실행
Set-Location ..\..
mvn -f services\api\pom.xml test
mvn -f services\mcp-server\pom.xml test

# Aspire: .NET SDK가 준비된 환경에서 실행
dotnet build aspire\AppHost\AppHost.csproj

# 최종 변경 검사
git diff --check
git status --short
```

현재 개발 환경에는 Java 21과 .NET SDK가 확인되지 않았다. Maven CLI는 PATH에 없지만 로컬 Maven 3.9.9 배포본은 있으며, 기본 Java는 17이라 Java 21 프로젝트 테스트를 아직 실행할 수 없다. Python 테스트 의존성 설치는 완료 전에 중단했으므로 pytest를 설치한 뒤 실행 결과를 기록한다.

## 커밋·원격 반영 절차

1. 각 담당자는 소유 경로의 변경과 검증 결과를 담당자 4에게 전달한다. 공용 세션에서 작업한다면 파일을 겹쳐 수정하지 않고 커밋도 동시에 실행하지 않는다.
2. 담당자 4가 `git diff --check`, 기능별 테스트, 전체 `git status`를 확인한다.
3. 변경을 기능별로 분리해 커밋한다. 권장 제목:
   - `feat(api): persist demo sessions and enforce match approvals`
   - `feat(agent): structure job requests and orchestrate matching tools`
   - `feat(frontend): connect farmer and worker matching flow`
   - `feat(aspire): configure the verified Azure deployment path`
   - 필요하면 문서 변경을 별도 `docs: document implementation and deployment handoff` 커밋으로 분리한다.
4. 코드 변경은 테스트 결과를, 인프라 변경은 선택된 배포 타깃과 검증 범위를 커밋 설명에 남긴다. 저장소의 커밋 규칙에 따라 Co-authored-by trailer를 포함한다.
5. 후속 기능 브랜치에서 `git fetch origin`으로 최신 기준을 확인하고, `feat/mvp-matching-flow`에 새 커밋을 push한다. `main`에 직접 push하지 않는다. 원격에 새 커밋이 있으면 강제 push하지 말고 먼저 통합한다.

## 보안·배포 주의

- 이 저장소에는 실제 사람의 정보나 실제 비밀값을 넣지 않는다.
- Azure DB에 대한 현재 Bicep 네트워크 설정은 공개 접근을 포함할 수 있으므로 실제 공개 서비스 전에 네트워크 경계를 검토한다.
- Dockerfile을 유지한다면 ACA가 이미지를 가져올 레지스트리와 이미지 게시 절차를 함께 검증한다.
- Aspire를 배포 경로로 선택한다면 설치된 Aspire 버전·Azure 타깃 패키지·Java/Python/NPM 리소스 지원을 공식 문서와 실제 배포 전 검증으로 확인한다.
- 성공적인 로컬 빌드는 Azure 배포 성공의 증거가 아니다. 배포 자격 증명 없이 Azure 배포가 끝났다고 주장하지 않는다.
