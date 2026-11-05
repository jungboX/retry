# TRD: 청도 일손이음

## 1. 기술 목표

3시간 안에 심사 가능한 웹 앱을 완성한다. 서비스 경계를 보여주되, 각 서비스는 하나의 핵심 흐름을 끝까지 실행하는 데 필요한 최소 기능만 가진다.

핵심 원칙:

- 벡터 검색 없이 구조화된 조건과 결정론적 점수로 추천한다.
- LLM은 자연어를 구조화하고 설명하는 데 사용한다.
- 업무 데이터 변경과 점수 계산은 API 또는 MCP 도구가 담당한다.
- 승인 없이는 매칭 상태를 변경하지 않는다.

## 2. 기술 스택

```text
Frontend        React + TypeScript + Vite + Material UI
API             Java 21 + Spring Boot 4.1.2 + Maven
Java AI         Spring AI 2.0.2
Agent           Python + Microsoft Agent Framework
Model 연결      GitHub Copilot SDK
MCP             Spring AI MCP Server
Database        PostgreSQL 또는 로컬 H2 fallback
Local runtime   .NET Aspire
Cloud           Azure Container Apps + Azure Static Web Apps
```

Spring AI 버전은 Spring Boot 버전이 아니라 Spring AI BOM으로 관리한다. `Spring AI 4.1.2`는 사용하지 않는다.

## 3. 서비스 구조

```text
React Web
  -> Spring Boot API
      -> Agent Service
          -> GitHub Copilot SDK
          -> MCP Server
              -> Spring Boot API / Repository
                  -> PostgreSQL 또는 데모 저장소
```

### Web

- 농가 데모
- 구직자 데모
- 작업 조건 미리보기
- 후보 비교
- 양쪽 승인 상태
- Agent 진행 상태 표시

### API

Spring Boot가 업무 API와 데모 상태를 제공한다.

```text
POST /api/jobs/analyze
POST /api/jobs
GET  /api/jobs/{jobId}/matches
POST /api/matches/{matchId}/farmer-approve
POST /api/matches/{matchId}/worker-accept
GET  /api/demo/state
GET  /actuator/health
```

API는 사용자 요청을 Agent Service로 전달하고, 승인·상태 변경은 직접 처리한다.

### Agent Service

Microsoft Agent Framework의 최소 순차 워크플로우를 사용한다.

```text
JobRegistrationAgent
  -> SafetyReviewAgent
  -> MatchingAgent
  -> ExplanationAgent
```

Agent Service의 책임:

- 자연어 작업 요청을 구조화된 JSON으로 변환
- 누락 필드와 위험 작업 확인
- MCP 도구 호출
- 스트리밍 또는 단계별 이벤트 반환
- 추천 결과를 사람이 이해할 수 있는 문장으로 설명

Agent Service는 별도 Python HTTP 서비스로 둔다. Java 내부에 Microsoft Agent Framework가 있다고 문서화하지 않는다.

### MCP Server

Spring AI MCP Server로 구현한다.

```text
parse_job_request
find_candidate_workers
calculate_match_score
check_job_safety
get_match_details
```

MCP 도구는 작고 결정론적으로 만든다.

- `find_candidate_workers`: 데모 구직자 목록에서 날짜·지역·작업 조건을 필터링한다.
- `calculate_match_score`: 고정된 가중치로 점수를 계산한다.
- `check_job_safety`: 위험 작업과 필수 확인 필드를 반환한다.
- `get_match_details`: 후보의 점수 근거를 반환한다.

매칭 확정 도구는 MCP가 호출하지 않는다. 승인 API가 상태 변경을 담당하여 사람의 통제를 보장한다.

## 4. 데이터 모델

### Job

```text
id
rawRequest
workType
location
startDate
endDate
workersNeeded
dailyPay
mealProvided
hazardFlags
status
```

### Worker

```text
id
nickname
availableStart
availableEnd
homeLocation
travelRadiusKm
workTypes
experienceLevel
status
```

### Match

```text
id
jobId
workerId
totalScore
scoreReasons
farmerApproved
workerAccepted
status
```

상태:

```text
DRAFT -> CANDIDATES_READY -> FARMER_APPROVED -> CONFIRMED
```

## 5. LLM 출력 계약

LLM 응답을 자유 텍스트로 바로 사용하지 않는다. 다음 구조로 변환하고 서버에서 필수 필드를 검증한다.

```json
{
  "workType": "복숭아 적과",
  "location": "청도군 매전면",
  "startDate": "2026-11-09",
  "endDate": "2026-11-11",
  "workersNeeded": 3,
  "dailyPay": 120000,
  "mealProvided": true,
  "missingFields": [],
  "hazardFlags": []
}
```

구조화에 실패하면 재시도하지 않고 사용자에게 직접 입력을 요청한다.

## 6. 프런트엔드 화면

```text
/                 역할 선택
/farmer-demo      농가 요청 입력과 후보 승인
/worker-demo      구직자 정보와 수락
/matching/:id     매칭 진행 상태
```

심사 편의를 위해 게스트 데모를 기본 경로로 사용한다. 로그인, 초대 코드, 별도 비밀번호를 요구하지 않는다.

## 7. 환경 변수

```text
MODEL_ENDPOINT=
MODEL_NAME=
COPILOT_SDK_CONFIG=
DATABASE_URL=
MCP_SERVER_URL=http://localhost:8082
AGENT_SERVICE_URL=http://localhost:8000
```

`.env` 파일은 커밋하지 않는다. 저장소에는 `.env.example`만 둔다.

## 8. 로컬 실행

최소 실행 순서:

```text
1. PostgreSQL 또는 H2 시작
2. MCP Server 시작
3. Agent Service 시작
4. Spring Boot API 시작
5. React 시작
```

Aspire AppHost는 위 서비스를 한 번에 실행하고 health check와 trace를 제공한다. Aspire가 준비되지 않은 경우에는 각 서비스의 개별 실행 명령으로 데모를 먼저 완성한다.

## 9. Azure 배포

시간이 부족하므로 다음 구성을 우선한다.

```text
React       Azure Static Web Apps
API         Azure Container Apps
Agent       Azure Container Apps
MCP         API Container Apps 내부 서비스
Database    Azure Database for PostgreSQL Flexible Server
```

Azure AI Search는 이번 MVP에서 사용하지 않는다. 서비스 수를 늘려 형식적인 Azure 통합으로 보이지 않도록 한다.

## 10. 비기능 요구사항

- API와 Agent에 요청 ID를 포함한다.
- `/actuator/health`를 제공한다.
- AI 요청 실패 시 데모용 설명과 재시도 버튼을 제공한다.
- 로그에 원문 개인정보를 남기지 않는다.
- 사용자가 승인하기 전에는 상태 변경 API를 호출하지 않는다.
