# Retry

청도 농가 일손 연결 데모 프로젝트입니다. 자연어 일손 요청을 구조화하고, 후보를 추천하며, 농가와 구직자 승인 후 매칭을 완료하는 흐름을 중심으로 구성합니다.

## 서비스 구조

- frontend: React + TypeScript + Vite
- services/api: Java 21 + Spring Boot 4.1.2 + Maven
- services/agent: Python + Microsoft Agent Framework
- services/mcp-server: Java + Spring AI MCP Server
- aspire: .NET Aspire AppHost 및 로컬 구성
- infra: Azure 배포 리소스
- shared: OpenAPI 계약

## 로컬 실행 순서

1. PostgreSQL 또는 H2 실행
2. MCP Server 실행
3. Agent Service 실행
4. Spring Boot API 실행
5. React 앱 실행

## 핵심 경로

- /: 역할 선택
- /farmer-demo: 농가 요청 입력과 후보 승인
- /worker-demo: 구직자 수락
- /matching/:id: 매칭 상태 표시
