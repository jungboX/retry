# API 명세

현재 구현 기준은 `shared/openapi.yaml`과 `JobsController`, `DemoSessionStore`입니다. 로그인 없이 농가 요청부터 양측 승인까지 진행하는 공개 데모 API입니다.

## 실행과 연결

- `npm run dev`는 Vite 프런트만 실행합니다. 프런트의 `/api` 요청은 `localhost:8081`로 프록시하므로 API가 따로 실행 중이어야 합니다.
- API의 분석 요청은 Agent(`8000`)를 호출하고, Agent는 MCP(`8082`)와 Copilot SDK를 사용합니다. DB도 준비되어 있어야 전체 흐름이 동작합니다.
- 전체 로컬 구성은 저장소 루트에서 Aspire AppHost(`aspire run`)를 기준으로 실행합니다. Aspire가 PostgreSQL, MCP, Agent, API, 웹과 게이트웨이 연결을 구성합니다. 현재 AppHost는 .NET 10/Aspire 13.6.4 기준입니다.
- 로컬 Vite 개발 서버 주소는 `5173`, API `8081`, Agent `8000`, MCP `8082`입니다. 배포 모델은 YARP 게이트웨이만 외부에 공개하고 `/api`를 API로 전달합니다.
- 자연어 분석에는 Agent 실행 설정과 Copilot 인증 토큰이 필요합니다. 비밀값을 코드나 문서에 저장하지 않습니다.

## 세션과 로그인

- 로그인·계정 인증은 없습니다.
- 프런트는 브라우저 `localStorage`에 데모 UUID를 보관하고 모든 업무 API 요청에 `X-Demo-Session-Id` 헤더로 보냅니다.
- 이 UUID는 브라우저별 데모 상태를 나누는 식별자일 뿐, 본인 확인이나 접근 권한 검증 수단이 아닙니다.
- 농가와 구직자 화면은 같은 브라우저 세션을 공유합니다. 다른 기기·브라우저에서는 같은 세션 ID를 전달하지 않는 한 작업을 볼 수 없습니다.
- 세션 상태는 DB에 저장되고 마지막 접근 후 4시간이 지나면 만료됩니다. 세션 수가 1,000개에 도달하면 새 세션 요청은 서비스 이용 불가 응답을 받습니다.

`/actuator/health`를 제외한 모든 경로에 세션 헤더가 필요합니다.

## 핵심 사용자 흐름

1. 농가가 자연어 작업 요청을 분석합니다.
2. 화면에서 AI 분석 결과와 누락 조건을 확인합니다. 날짜·위치·임금 등 필수 조건이 누락되면 작업을 만들지 말고 요청을 수정해 다시 분석합니다.
3. 조건이 유효하면 분석 ID로 작업과 추천 후보를 생성합니다.
4. 위험 작업이면 안전 확인을 기록한 뒤 농가가 후보를 승인합니다.
5. 구직자가 승인된 후보를 수락합니다. 농가 승인과 구직자 수락이 모두 완료되어야 `CONFIRMED`가 됩니다.
6. 새로고침 후에는 상태 조회 API로 현재 세션의 작업과 매칭을 복구합니다.

## 엔드포인트

모든 요청·응답 본문은 JSON입니다.

| 메서드 및 경로 | 용도 | 성공 응답 |
|---|---|---|
| `POST /api/jobs/analyze` | 자연어 요청 분석 및 후보 추천 | `200`, `{ analysisId, analysis }` |
| `POST /api/jobs` | 사용자가 확인한 분석으로 작업 생성 | `201`, `{ id, status, job, matches, safetyConfirmed }` |
| `GET /api/jobs/{jobId}/matches` | 작업의 후보를 점수순으로 조회 | `200`, `Match[]` |
| `POST /api/jobs/{jobId}/safety-confirm` | 안전 확인 상태 기록 | `200`, `{ jobId, safetyConfirmed, status }` |
| `POST /api/matches/{matchId}/farmer-approve` | 농가 승인 | `200`, 갱신된 `Match` |
| `POST /api/matches/{matchId}/worker-accept` | 구직자 수락 | `200`, 갱신된 `Match` |
| `GET /api/demo/state` | 세션의 현재 작업·매칭 복구 | `200`, `{ jobId, job, safetyConfirmed, matches }` |
| `GET /actuator/health` | API 헬스 확인 | `200` |

### 요청 예시

```http
POST /api/jobs/analyze
Content-Type: application/json
X-Demo-Session-Id: 2bd9aef7-41fc-4baa-9916-2a7b272267a4

{"rawRequest":"복숭아 수확 작업자 3명, 2026-11-09부터 2026-11-11까지, 일급 120000원"}
```

```json
{
  "analysisId": "분석 결과가 반환한 UUID",
  "analysis": {
    "job": {
      "workType": "복숭아 수확",
      "location": "청도군",
      "startDate": "2026-11-09",
      "endDate": "2026-11-11",
      "workersNeeded": 3,
      "dailyPay": 120000,
      "mealProvided": null,
      "hazardFlags": [],
      "missingFields": []
    },
    "recommendedWorkers": [],
    "status": "CANDIDATES_READY",
    "explanation": "추천 근거 설명"
  }
}
```

분석 응답의 `analysisId`를 다음 작업 생성 요청에 전달합니다.

```http
POST /api/jobs
Content-Type: application/json
X-Demo-Session-Id: 2bd9aef7-41fc-4baa-9916-2a7b272267a4

{"analysisId":"분석 결과가 반환한 UUID"}
```

안전 확인과 승인은 상태 변경이므로 성공 응답을 받은 뒤 화면 상태를 갱신합니다.

```json
{"confirmed":true}
```

`worker-accept`에는 요청 본문이 필요하지 않습니다. 후보가 없으면 작업 생성은 성공하고 `status`가 `NO_CANDIDATES`, `matches`가 빈 배열입니다.

## 상태 및 오류 처리

| HTTP 상태 | 의미와 화면 복구 방법 |
|---|---|
| `400` | JSON, 필수 헤더, 필드 형식이 잘못됨. 입력을 확인하고 다시 요청합니다. 자연어 요청은 1~4,000자입니다. |
| `404` | 현재 세션에서 작업 또는 매칭을 찾을 수 없음. 세션 ID와 최신 상태를 다시 확인합니다. |
| `409` | 분석 만료, 승인 순서 오류, 안전 확인 누락 등 상태 충돌. 오류 메시지에 따라 다시 분석하거나 먼저 필요한 승인을 진행합니다. |
| `422` | 필수 작업 조건이 누락되었거나 유효하지 않음. 표시된 조건을 사용자에게 확인하고 다시 분석합니다. |
| `503` | 현재 API 구현에서 Agent 호출 실패 또는 저장소 사용 불가. 완료로 표시하지 말고 재시도 안내와 상태 확인을 제공합니다. |

공통 오류 본문은 `{ status, error, message, timestamp }` 형식입니다. 성공 응답이 오기 전에는 버튼 처리 완료나 매칭 확정으로 표시하지 않습니다. 최종 매칭 상태는 `Match.status == "CONFIRMED"`로 확인합니다.

## 현재 동작하지 않는 이유

프런트 화면만 실행 중이고, 화면이 호출하는 API(`8081`), Agent(`8000`), MCP(`8082`)는 확인 시 실행 중이 아니었습니다. 따라서 프런트는 표시되더라도 API 요청이 실패하고, API만 실행해도 Agent·MCP가 없으면 분석·추천 단계가 완료되지 않습니다. Agent에는 Copilot SDK 실행에 필요한 런타임 설정도 필요합니다. 전체 서비스는 Aspire로 함께 실행해야 하며, API만 단독 실행할 때도 Agent, MCP와 DB 연결이 준비되어야 합니다.

## 코드·설계 일치 및 제한

- 경로, 헤더, 필드, 응답 스키마의 기준은 `shared/openapi.yaml`입니다.
- API는 필수 조건과 후보 결과를 검증하고 승인 상태를 관리합니다. Agent가 승인이나 최종 확정을 수행하지 않습니다.
- 추천 점수 계산은 MCP 책임이며, API는 MCP가 반환한 점수와 근거를 상태에 보존합니다.
- 현재 로그인 없는 세션은 데모용입니다. 세션 UUID만으로 권한을 증명할 수 없으므로 실제 개인정보나 운영 업무에 사용하면 안 됩니다.
- API 빌드·단위 테스트는 통과했지만, 실제 Azure PostgreSQL 연결과 전체 Aspire 배포 흐름은 별도 통합 검증이 필요합니다.
