from __future__ import annotations

from typing import Any

from fastapi import FastAPI

from retry_agent.agents.job_registration_agent import JobRegistrationAgent
from retry_agent.workflows.matching_workflow import MatchingWorkflow

app = FastAPI(title="Retry Agent Service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/jobs/analyze")
def analyze_job(payload: dict[str, Any]) -> dict[str, Any]:
    agent = JobRegistrationAgent(raw_request=str(payload.get("rawRequest", "")))
    parsed = agent.run()
    workflow = MatchingWorkflow()
    return workflow.run(parsed)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
