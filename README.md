# Retry

청도 농가 일손 연결 데모 프로젝트입니다. 자연어 일손 요청을 구조화하고, 후보를 추천하며, 농가와 구직자 승인 후 매칭을 완료하는 흐름을 중심으로 구성합니다.

## 서비스 구조

- frontend: React + TypeScript + Vite
- services/api: Java 21 + Spring Boot 4.1.2 + Maven
- services/agent: Python + Microsoft Agent Framework
- services/mcp-server: Java + Spring AI MCP Server
- aspire: .NET Aspire AppHost (로컬 실행, `aspire deploy` Azure 배포)
- infra: 이전 Bicep 산출물 (Aspire 배포 검증 후 삭제 여부 결정)
- shared: OpenAPI 계약

협업 규칙과 담당 영역은 [HANDOFF.md](HANDOFF.md), 제품·기술 기준은 `PRD.md`, `TDD.md`, `TRD.md`를 따른다.

## 서비스 포트

| 서비스 | 포트 |
|---|---|
| frontend (Vite) | 5173 |
| services/api | 8081 |
| services/agent | 8000 |
| services/mcp-server | 8082 |

환경 변수 이름은 `.env.example`에 있다. 값은 저장소에 넣지 않는다.

## 로컬 실행 순서

각 서비스를 따로 실행할 때는 다음 순서를 따른다. 통합 확인은 Aspire AppHost를 기준으로 한다.

1. PostgreSQL 또는 H2 (기본값은 H2 파일 DB)
2. MCP Server: `mvn -f services/mcp-server/pom.xml spring-boot:run`
3. Agent Service: `services/agent`에서 `PYTHONPATH=src python -m uvicorn retry_agent.main:app --port 8000`
4. Spring Boot API: `mvn -f services/api/pom.xml spring-boot:run`
5. React 앱: `npm --prefix frontend run dev`

## 테스트

```bash
npm --prefix frontend run build
(cd services/agent && python -m pytest -q)
mvn -f services/api/pom.xml test
mvn -f services/mcp-server/pom.xml test
```

Java 21, Maven, Python 3.11 이상이 필요하다. Agent 테스트 전에 `services/agent`에서 `pip install -e ".[dev]"`로 개발 의존성을 설치한다.

## 핵심 경로

- /: 역할 선택
- /farmer-demo: 농가 요청 입력과 후보 승인
- /worker-demo: 구직자 수락
- /matching/:id: 매칭 상태 표시
