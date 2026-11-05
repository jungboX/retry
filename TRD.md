# TRD: 청도 일손이음

## 문서 기준

- 아이디어: [IDEATION.md](IDEATION.md)
- 제품 요구사항: [PRD.md](PRD.md)
- 설계 근거: [TDD.md](TDD.md)
- 문서 상태: 검토용 기술 기준

TRD는 PRD의 사용자 요구를 기술 구성, 서비스 경계, 인터페이스, 데이터, 운영 및 배포 요구로 구체화한다. 기술 선택의 이유와 대안은 TDD를 기준으로 한다.

## 1. 기술 목표와 필수 조건

- PRD 핵심 흐름을 처음부터 양쪽 승인과 최종 상태까지 실행한다.
- GitHub Copilot SDK와 Microsoft Agent Framework를 해커톤의 필수 기술로 실제 기능에 사용한다. 두 기술의 통합 방식은 배포 전 호환성 검증을 통과해야 한다.
- 에이전트는 명시적인 순차 워크플로를 따른다. LLM은 최종 점수 계산, 승인, 매칭 확정을 하지 않는다.
- 심사자가 로그인 없이 사용할 수 있는 웹 앱을 Azure에 배포한다. 배포는 Aspire AppHost에서 `aspire deploy` 한 경로로 반복 실행할 수 있어야 한다.
- 데모 데이터만 사용하고, 비밀값은 소스 코드나 저장소에 저장하지 않는다.
- API 계약은 `shared/openapi.yaml`을 단일 기준으로 유지한다.

## 2. 기술 스택

| 영역 | 기준 기술 | 책임 |
|---|---|---|
| 웹 | React, TypeScript, Vite | 화면, API 요청, 진행 상태 표시 |
| 업무 API | Java 21, Spring Boot 4.1.2, Maven | 업무 요청, 검증, 승인 상태 |
| Agent | Python, Microsoft Agent Framework | 순차 워크플로와 단계 간 제어 |
| Copilot SDK | GitHub Copilot SDK | 해커톤 필수 SDK를 활용한 Agent/LLM 실행. 정확한 연동 경계는 호환성 검증 후 확정 |
| MCP | Java, Spring AI 2.0.2 MCP Server | 후보 조회, 안전 검토, 점수 및 근거 계산 도구 |
| 데이터 | PostgreSQL | 로컬 및 Azure에서 일관된 데모 상태 저장 |
| 오케스트레이션·배포 | .NET Aspire (`aspire run`, `aspire deploy`) | 로컬 서비스 실행·연결·헬스 체크·추적, Azure 배포의 단일 진입점 |
| 컴퓨트 | Azure Container Apps (Aspire Azure Container Apps 환경) | 웹(정적 파일 서빙), API, Agent, MCP 컨테이너 실행 |
| 이미지 저장소 | Azure Container Registry | Aspire가 배포 시 프로비저닝하고 이미지를 빌드·게시한다 |
| 관리형 데이터 | Azure Database for PostgreSQL Flexible Server | 배포 환경의 데모 데이터 저장 |

Spring AI 버전은 Spring Boot 버전과 별개로 BOM에서 관리한다. 구현 전에 선택한 프레임워크 버전의 상호 호환성을 확인한다. AppHost는 Aspire 13.6.4와 .NET 10 SDK(`net10.0`)를 사용하며, 빌드와 `aspire publish`(Bicep 생성)까지 확인했다. 실제 `aspire deploy`는 아직 검증하지 않았다.

## 3. 서비스 경계와 호출 흐름

```text
React Web
  -> Spring Boot API
      -> Python Agent Service
          -> Microsoft Agent Framework workflow
              -> GitHub Copilot SDK (검증할 LLM/Agent 실행 경계)
              -> MCP Server
                  -> 후보 조회·안전 검토·점수 계산
      -> 승인 및 매칭 상태 관리
          -> PostgreSQL
```

웹 앱은 업무 API만 호출한다. API는 Agent 분석을 요청하고, 승인 상태 변경을 직접 처리한다. Agent는 명시적으로 허용된 MCP 도구만 호출한다. MCP Server는 승인 또는 최종 상태 변경 기능을 제공하지 않는다.

### 서비스 간 계약 소유

| 경계 | 계약 | 소유 |
|---|---|---|
| Web → API | `shared/openapi.yaml` (모든 요청에 `X-Demo-Session-Id` 헤더) | 담당자 1 |
| API → Agent | `POST /api/jobs/analyze` 요청 `{ rawRequest: string(1~4000자) }`, 응답은 분석 결과 JSON. 실패는 502(도구 연결 실패)·503(분석 실패) | 담당자 2 (API 쪽 `AgentClient` 변경은 담당자 1과 합의) |
| Agent → MCP | 4개 도구의 이름·인자·응답 (§4) | 담당자 2 |

계약을 바꾸는 사람은 소유자에게 먼저 PR로 제안하고, 소비 측은 그 PR이 머지된 뒤 따라간다.

Copilot SDK와 Agent Framework의 호출 순서·도구 권한·오류 전달은 통합 호환성 검증의 대상이다. 검증에 실패하면 기술 조합과 연결 경계를 재검토하고, SDK를 이름만 사용하는 구현으로 대체하지 않는다.

## 4. Agent 워크플로 및 MCP 도구

### 순차 워크플로

```text
요청 조건 정리
  -> 필수 항목 및 안전 확인
  -> 후보 검색 및 고정 규칙 점수 계산
  -> 추천 근거 설명
```

각 단계는 진행 상태를 반환한다. 필수 날짜·위치·임금이 없으면 후보 검색을 수행하지 않는다. 구조화된 모델 출력은 API 경계에서 형식과 필수 값을 검증한다.

### MCP 도구

- `find_candidate_workers`: 데모 구직자 중 요청 조건에 맞는 후보를 찾는다.
- `calculate_match_score`: PRD에 정의된 점수 정책에 따라 후보별 점수와 근거를 계산한다.
- `check_job_safety`: 위험 작업과 필요한 추가 확인을 반환한다.
- `get_match_details`: 후보별 일치·불일치 조건을 조회한다.

점수 가중치의 기준은 PRD의 PR-03이다. 점수 정책이 변경되면 PRD와 구현 테스트를 함께 갱신한다. MCP는 자연어 요청을 AI 대신 임의 해석하거나 최종 매칭 상태를 변경하지 않는다.

## 5. 업무 API와 데이터

### API 초안

실제 계약은 `shared/openapi.yaml`에 정의하고 이 목록과 동기화한다. 현재 계약에는 일부 응답 본문과 오류 응답 형식이 아직 정의되지 않았으므로, 해당 기능을 구현하기 전에 계약과 복구 가능한 오류 형식을 보완한다.

```text
POST /api/jobs/analyze
POST /api/jobs
GET  /api/jobs/{jobId}/matches
POST /api/jobs/{jobId}/safety-confirm
POST /api/matches/{matchId}/farmer-approve
POST /api/matches/{matchId}/worker-accept
GET  /api/demo/state
GET  /actuator/health
```

`/actuator/health`를 제외한 모든 요청은 브라우저가 만든 `X-Demo-Session-Id`(UUID) 헤더로 데모 세션을 구분한다.

오류 응답은 입력 누락, 분석 실패, 후보 없음, 승인 충돌을 구분하고 사용자가 복구할 수 있는 정보를 포함한다. 중복 승인과 잘못된 상태 전이는 최종 상태를 변경하지 않아야 한다.

### 최소 데이터 모델

```text
Job
  id, rawRequest, workType, location, startDate, endDate,
  workersNeeded, dailyPay, mealProvided, hazardFlags, status

Worker
  id, nickname, availableStart, availableEnd, homeLocation,
  travelRadiusKm, workTypes, experienceLevel, status

Match
  id, jobId, workerId, totalScore, scoreReasons,
  farmerApproved, workerAccepted, status
```

실제 전화번호와 상세 주소는 저장하지 않는다. `CONFIRMED` 상태는 농가 승인과 구직자 수락이 모두 완료된 경우에만 설정한다.

## 6. 웹 화면과 공개 접근

```text
/                 역할 선택
/farmer-demo      농가 요청, 조건 확인, 후보 승인
/worker-demo      구직자 역할, 조건 확인, 수락
/matching/:id     승인 및 최종 상태
```

웹 앱은 상대 경로(`/api/...`)로 API를 호출한다. 배포 환경에서는 웹을 서빙하는 컨테이너가 `/api`를 API로 프록시하므로 같은 출처에서 동작하고 CORS가 필요 없다. 로컬 개발 서버도 `/api`를 API로 프록시한다.

로그인, 초대 코드, 별도 비밀번호 없이 PRD의 전체 핵심 여정을 수행할 수 있어야 한다. 공개 데모는 가짜 데이터만 다루며, 새로고침 후 데모 상태를 확인할 수 있어야 한다. 동시 방문자의 상태 격리와 데모 초기화 방식은 구현 전에 결정하고 검증한다.

## 7. 보안, 개인정보 및 신뢰

- 자연어 요청은 신뢰할 수 없는 입력으로 처리한다. 요청 안의 지시문이 시스템 지침이나 도구 권한을 바꾸지 못하게 한다.
- Agent가 호출할 MCP 도구를 허용 목록으로 제한한다. 승인·상태 변경 기능은 Agent 도구에 제공하지 않는다.
- 모델 출력은 필수 필드, 값 범위, 데이터 형식을 검증한 뒤 사용한다.
- 로그와 추적 정보에는 실제 개인정보, 비밀값, 불필요한 원문 요청을 기록하지 않는다.
- 로컬 비밀값은 환경 변수로 주입하고 커밋하지 않는다. Azure의 모델 인증 및 비밀값 저장 방식은 배포 환경에서 검증하며, 코드와 공개 문서에 자격 증명을 넣지 않는다.
- AI가 만든 조건과 설명은 화면에 `AI 분석 결과`로 표시한다.
- 모델 오류, MCP 오류, 후보 없음, 승인 충돌은 성공으로 가장하지 않고 복구 가능한 상태로 반환한다.

## 8. 환경 설정

저장소의 `.env.example`에는 값이 아닌 변수 이름과 필요한 설명만 기록한다.

```text
MODEL_NAME
COPILOT_GITHUB_TOKEN
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
MCP_SERVER_URL
AGENT_SERVICE_URL
CORS_ALLOWED_ORIGINS
VITE_API_BASE_URL
Azure__SubscriptionId
Azure__Location
Azure__ResourceGroup
Parameters__copilotGithubToken
```

`Azure__*`와 `Parameters__*`는 `aspire deploy`(특히 CI)에 전달하는 배포 입력이다. 실제 토큰, 비밀번호, 연결 문자열은 예제 파일이나 소스에 넣지 않는다. Aspire는 PostgreSQL 연결 값을 `ConnectionStrings__retry`로 주입하고 API가 이를 읽는다. 개발자가 각 서비스를 따로 실행할 때는 `DATABASE_URL`에 JDBC URL을 지정한다. `VITE_API_BASE_URL`은 로컬에서 비워 두며, 배포 환경은 상대 경로를 쓰므로 설정하지 않는다.

## 9. Aspire AppHost 구성

Aspire AppHost는 웹, API, Agent, MCP, PostgreSQL의 실행 관계를 관리하며, 로컬 실행(`aspire run`)과 Azure 배포(`aspire deploy`)가 같은 AppHost 모델을 사용한다. 서비스가 준비되지 않았거나 연결할 수 없는 경우 상태를 확인할 수 있어야 한다.

배포 모델의 기준:

- API, Agent, MCP는 컨테이너 리소스로 모델링한다. 기존 Dockerfile(`services/*/Dockerfile`)을 사용하며, 이 Dockerfile은 저장소 루트를 빌드 컨텍스트로 가정한다. 현재 AppHost의 `AddExecutable`(`mvn`, `python`) 리소스는 컨테이너 배포 대상이 될 수 없으므로 배포 경로에서 교체한다.
- 웹은 Vite 앱 리소스로 두고, YARP 게이트웨이 리소스가 빌드된 정적 파일을 서빙하면서 `/api`를 API로 프록시한다(`PublishWithStaticFiles`). 게이트웨이만 외부 공개 엔드포인트를 가지며 API, Agent, MCP는 내부 접근으로 둔다. `PublishAsStaticWebsite`는 `apiTarget`이 서비스 디스커버리 리소스여야 해서 Dockerfile 컨테이너 API에는 쓸 수 없고, 로컬 개발은 Vite 개발 서버(5173)가 `localhost:8081`로 프록시한다. 이 게시 API는 공식 문서에서 실험적(`ASPIREJAVASCRIPT001`)이다.
- PostgreSQL은 로컬에서는 컨테이너, Azure에서는 관리형 PostgreSQL을 사용하도록 모델링하고, API는 `ConnectionStrings__retry`를 통해 연결한다.
- Copilot 토큰은 비밀 매개변수로 선언해 Agent에 환경 변수로 주입하며, 소스와 저장소에 값을 두지 않는다.

관찰 가능성:

- 각 서비스의 health check를 Aspire 대시보드에서 확인한다.
- API 요청부터 Agent/MCP 호출까지 상관관계가 있는 trace를 확인한다.
- 비밀값과 원문 개인정보는 trace에 포함하지 않는다.
- 개발자는 각 서비스만 따로 실행할 수도 있지만, 통합 확인은 Aspire AppHost를 기준으로 한다.

## 10. Azure 배포

| 구성 요소 | 배포 위치 |
|---|---|
| React Web | Azure Container Apps (외부 공개, 정적 파일 서빙 + `/api` 프록시) |
| Spring Boot API | Azure Container Apps (내부) |
| Python Agent Service | Azure Container Apps (내부) |
| MCP Server | Azure Container Apps (내부) |
| 이미지 저장소 | Azure Container Registry (Aspire가 프로비저닝) |
| Database | Azure Database for PostgreSQL Flexible Server |

배포 앱은 `*.azurecontainerapps.io` Azure 기본 도메인을 사용한다. 커스텀 도메인은 심사 URL로 사용하지 않는다. API와 Agent에 요청 ID를 전달하고 헬스 상태와 실패 원인을 관찰할 수 있어야 한다.

배포는 Aspire 공식 문서의 Azure 배포 모델을 따른다. `aspire deploy`는 AppHost의 Azure Container Apps 환경을 대상으로 파라미터를 해석하고, 인프라를 프로비저닝하고, 컨테이너 이미지를 빌드·게시하고, 관리 ID로 이미지 pull 권한을 구성한 뒤 각 리소스를 배포한다. 배포 대상(컴퓨트 환경)이 AppHost에 없으면 `aspire deploy`는 아무 작업도 하지 않으므로, 이를 추가하는 것이 배포 작업의 첫 단계다.

```bash
az login

# 로컬 배포: 누락된 값은 첫 배포 때 프롬프트로 묻거나 아래처럼 로컬 비밀로 저장한다.
aspire secret set "Azure:SubscriptionId" "<subscription-id>"
aspire secret set "Azure:Location" "<region>"
aspire secret set "Azure:ResourceGroup" "<resource-group>"
aspire deploy --environment Production

# CI 배포: Azure__SubscriptionId, Azure__Location, Azure__ResourceGroup, Parameters__copilotGithubToken을
# 파이프라인 비밀·환경 변수로 전달한다. OIDC(워크로드 ID 연동)로 Azure에 로그인한다.
```

비밀 값은 저장소에 저장하지 말고 `aspire secret` 또는 CI 비밀 저장소로만 전달한다. 배포 리소스 삭제는 리소스 그룹 단위로 이루어지므로 실행 전 대상과 범위를 확인한다.

`infra/main.bicep`은 이전 배포 경로(Bicep + Static Web Apps)의 산출물이다. Aspire 배포가 검증된 뒤 삭제 여부를 결정하며, 그 전까지 이 파일로 배포했다고 보고하지 않는다. 두 경로를 혼합하지 않는다.

## 11. 기술 검증 및 미확정 위험

- **Copilot SDK와 Agent Framework 호환성:** 한 워크플로에서 SDK를 실제 호출하고 모델 응답·취소·오류·도구 권한이 전달되는지 배포 전에 검증한다.
- **모델 인증:** 로컬 개발과 Azure 컨테이너에서 각각 비대화형 인증이 가능한지 확인한다. 사용자 개인의 로컬 CLI 로그인에만 의존하지 않는다.
- **공개 데모 상태:** 무인증 환경에서 여러 심사자가 동시에 사용할 때 데이터가 섞이거나 기존 데모를 덮어쓰지 않는지 검증한다.
- **시간·날짜 해석:** “다음 주” 같은 상대 날짜는 기준 시간대를 명확히 하고, 사용자 확인 전에는 확정 조건으로 취급하지 않는다.
- **Aspire 배포 호환성:** `aspire publish`로 Bicep 생성은 확인했고, 외부 ingress는 게이트웨이 하나뿐이다. 실제 `aspire deploy`(이미지 빌드·푸시·프로비저닝)는 Azure 자격 증명이 필요해 아직 검증하지 않았다. 게이트웨이의 정적 게시 API는 실험적이므로 첫 실배포에서 정적 파일과 `/api` 라우팅을 확인한다.
- **관리형 DB 연결:** API는 관리형 Azure PostgreSQL에 TLS를 요구하도록 구현돼 있다(PR #2). 배포 후 실제 TLS 연결과 네트워크 접근 범위(공개 접근 여부)를 검증하고, 프로덕션 공개 전에 경계를 검토한다.
- **Dockerfile 빌드 컨텍스트:** AppHost는 세 Dockerfile을 저장소 루트 컨텍스트로 참조한다. agent와 api 이미지는 로컬에서 빌드가 성공했다. `services/mcp-server` 이미지는 pom에 테스트 의존성이 없어 `MatchToolsTest` 컴파일 오류로 빌드가 실패하며, 이는 배포와 로컬 `aspire run`을 막는다(담당자 2 수정 필요).
- **프런트엔드 프록시:** 로컬 개발은 Vite 개발 서버(5173)가 `localhost:8081`로 프록시하고, AppHost가 API 컨테이너를 호스트 8081에 고정 노출한다. 배포에서는 게이트웨이가 `/api`를 프록시하므로 프런트엔드는 상대 경로만 쓴다.

## 12. 기술 요구사항과 검증

다음 항목은 PRD와 TDD를 기술 수준으로 구체화한 제안이다. Copilot SDK 연동과 공개 데모 상태 격리는 구현 전에 검증해야 한다.

| ID | 기술 요구사항 | 근거 | 검증 기준 |
|---|---|---|---|
| TR-01 | Agent Framework 워크플로에서 Copilot SDK를 실제 호출하고, 결과 및 오류를 단계적으로 전달해야 한다. | PR-01, PR-07; TDD ADR-003 | 통합 테스트에서 SDK 호출, 구조화 결과, 실패 반환을 확인한다. |
| TR-02 | 필수 작업 조건이 유효하지 않으면 후보 조회나 승인 단계로 진행하지 않아야 한다. | PR-02; API 계약 | 필수 값 누락·잘못된 값의 요청에서 오류와 누락 항목을 확인하고 후보 도구가 호출되지 않음을 확인한다. |
| TR-03 | 추천 점수 계산은 PRD의 단일 점수 정책을 따르고 같은 입력에 같은 결과를 반환해야 한다. | PR-03; TDD ADR-001·002 | 경계값을 포함한 단위 테스트로 점수 합계와 반복 결과를 확인한다. |
| TR-04 | `CONFIRMED` 전이는 농가 승인과 구직자 수락이 모두 완료된 경우에만 허용해야 한다. | PR-05, PR-06; TDD ADR-007 | 양쪽 승인, 한쪽 승인, 중복 요청, 잘못된 상태 전이를 포함한 상태 테스트를 통과한다. |
| TR-05 | Agent 도구 권한은 허용 목록으로 제한하고 승인·상태 변경 기능을 제공하지 않아야 한다. | PR-04, PR-05, PR-06; TDD ADR-005 | 도구 목록과 권한 테스트에서 승인 도구가 없고, 비신뢰 입력이 도구 권한을 변경하지 못함을 확인한다. |
| TR-06 | 공개 데모는 새로고침 후 상태를 유지하고, 동시 사용자의 데이터가 섞이지 않도록 해야 한다. | PR-08; TDD ADR-006 | 두 독립 데모 세션의 생성·갱신·새로고침 테스트를 수행한다. |
| TR-07 | Aspire는 웹, API, Agent, MCP, PostgreSQL을 함께 실행하고 health check 및 요청 추적을 제공해야 한다. | IDEATION Layer 3; TDD ADR-008 | AppHost 시작 후 각 서비스의 준비 상태와 API→Agent→MCP trace를 확인한다. |
| TR-08 | 심사자는 Azure 기본 도메인에서 로그인 없이 핵심 여정을 수행할 수 있어야 한다. | PR-08; 해커톤 제출 안내 | 배포 URL에서 인증 없이 요청부터 양쪽 승인까지 E2E 시나리오를 수행한다. |
| TR-09 | Azure 배포는 `aspire deploy`로 반복 실행할 수 있어야 하며, 웹만 외부에 노출하고 API·Agent·MCP는 내부 접근이어야 한다. | PR-08; TDD ADR-008; 해커톤 평가 기준(반복 가능한 배포) | 새 리소스 그룹에 `aspire deploy`를 실행해 성공하고, 같은 명령 재실행이 오류 없이 끝나며, 외부에서 내부 서비스 URL에 직접 접근할 수 없음을 확인한다. |
| TR-10 | 비밀값과 불필요한 개인정보는 코드, 데모 데이터, 로그, trace에 포함되지 않아야 한다. | PR-09; AGENTS 데이터 규칙 | 저장소 변경분과 테스트 데이터·로그를 검사하고, Azure 런타임 설정에서 비밀값 노출 여부를 확인한다. |

## 13. 요구사항 추적

| PRD | 기술 반영 위치 |
|---|---|
| PR-01, PR-02 | Agent 워크플로, 구조화 결과 검증, 웹 조건 확인 |
| PR-03 | MCP 후보 조회·점수 계산, PRD 점수 정책 참조 |
| PR-04 | `check_job_safety`, 승인 전 위험 확인 |
| PR-05, PR-06 | 업무 API, `Match` 승인 상태 전이 |
| PR-07 | API/Agent/MCP 오류 모델과 사용자 복구 안내 |
| PR-08 | 공개 Azure URL, 게스트 데모 화면, `aspire deploy` 배포 (TR-08, TR-09) |
| PR-09 | 테스트 데이터, 비밀값 관리, 로그·trace 제한 |
