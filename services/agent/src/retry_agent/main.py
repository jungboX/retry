from __future__ import annotations

import logging
from typing import Any

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from retry_agent.agents.job_registration_agent import JobAnalysisError
from retry_agent.workflows.matching_workflow import MatchingWorkflow

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
app = FastAPI(title="Retry Agent Service")


class AnalysisRequest(BaseModel):
    rawRequest: str = Field(min_length=1, max_length=4_000)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/jobs/analyze")
async def analyze_job(payload: AnalysisRequest) -> dict[str, Any]:
    try:
        return await MatchingWorkflow().run(payload.rawRequest)
    except JobAnalysisError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except Exception as error:
        logger.error("Job analysis workflow failed: %s", type(error).__name__)
        raise HTTPException(
            status_code=502,
            detail="분석 도구에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        ) from error
