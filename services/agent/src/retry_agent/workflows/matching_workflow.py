from __future__ import annotations

import json
import os
from typing import Any, Never

from agent_framework import WorkflowBuilder, WorkflowContext, executor
from mcp import Client

from retry_agent.agents.job_registration_agent import JobRegistrationAgent


def _structured_result(result: Any) -> Any:
    structured = getattr(result, "structuredContent", None)
    if structured is not None:
        return structured
    structured = getattr(result, "structured_content", None)
    if structured is not None:
        return structured
    for item in getattr(result, "content", []):
        text = getattr(item, "text", None)
        if text:
            return json.loads(text)
    raise ValueError("MCP tool returned no structured result")


async def _call_tool(name: str, arguments: dict[str, Any]) -> Any:
    server_url = os.getenv("MCP_SERVER_URL", "http://localhost:8082").rstrip("/")
    if not server_url.endswith("/mcp"):
        server_url = f"{server_url}/mcp"
    async with Client(server=server_url) as client:
        result = await client.call_tool(name, arguments)
    if getattr(result, "isError", False) or getattr(result, "is_error", False):
        raise RuntimeError(f"MCP tool failed: {name}")
    return _structured_result(result)


@executor(id="job_analysis")
async def analyze_job(raw_request: str, context: WorkflowContext[dict[str, Any]]) -> None:
    analysis = await JobRegistrationAgent().run(raw_request)
    await context.send_message(analysis)


@executor(id="safety_review")
async def review_safety(analysis: dict[str, Any], context: WorkflowContext[dict[str, Any]]) -> None:
    safety = await _call_tool(
        "check_job_safety",
        {
            "workType": analysis.get("workType") or "",
            "hazardFlags": analysis.get("hazardFlags") or [],
        },
    )
    reviewed = {**analysis, "safetyReview": safety}
    await context.send_message(reviewed)


@executor(id="candidate_matching")
async def find_matches(reviewed: dict[str, Any], context: WorkflowContext[dict[str, Any]]) -> None:
    if reviewed["missingFields"]:
        await context.send_message({
            "job": reviewed,
            "recommendedWorkers": [],
            "status": "AWAITING_INPUT",
            "explanation": "날짜, 위치, 임금을 확인한 뒤 후보를 찾을 수 있습니다.",
        })
        return

    job = {key: reviewed[key] for key in (
        "workType", "location", "startDate", "endDate", "workersNeeded", "dailyPay"
    )}
    workers = await _call_tool("find_candidate_workers", job)
    if not isinstance(workers, list):
        raise ValueError("MCP candidate tool returned an invalid result")

    ranked: list[dict[str, Any]] = []
    for worker in workers:
        if not isinstance(worker, dict) or not isinstance(worker.get("id"), str):
            continue
        score = await _call_tool(
            "calculate_match_score",
            {
                "workerId": worker["id"],
                "workType": job["workType"],
                "startDate": job["startDate"],
                "endDate": job["endDate"],
                "dailyPay": job["dailyPay"],
            },
        )
        details = await _call_tool("get_match_details", {"workerId": worker["id"]})
        if not isinstance(score, dict) or not isinstance(details, dict):
            raise ValueError("MCP scoring tool returned an invalid result")
        ranked.append({
            "workerId": worker["id"],
            "nickname": worker.get("nickname", worker["id"]),
            "score": score.get("totalScore", 0),
            "scoreReasons": score.get("reasons", []),
            "details": details,
        })

    ranked.sort(key=lambda candidate: (-candidate["score"], candidate["workerId"]))
    await context.send_message({
        "job": reviewed,
        "recommendedWorkers": ranked,
        "status": "CANDIDATES_READY" if ranked else "NO_CANDIDATES",
        "explanation": (
            "추천 점수와 조건별 근거를 확인한 뒤 후보를 선택해 주세요."
            if ranked else "조건에 맞는 후보가 없습니다. 작업 조건을 확인해 주세요."
        ),
    })


@executor(id="result")
async def return_result(result: dict[str, Any], context: WorkflowContext[Never, dict[str, Any]]) -> None:
    await context.yield_output(result)


class MatchingWorkflow:
    """Run analysis, safety review, matching, and output in explicit sequence."""

    def __init__(self) -> None:
        self.workflow = (
            WorkflowBuilder(start_executor=analyze_job)
            .add_edge(analyze_job, review_safety)
            .add_edge(review_safety, find_matches)
            .add_edge(find_matches, return_result)
            .build()
        )

    async def run(self, raw_request: str) -> dict[str, Any]:
        result = await self.workflow.run(raw_request)
        outputs = result.get_outputs()
        if not outputs or not isinstance(outputs[0], dict):
            raise RuntimeError("매칭 워크플로에서 결과를 생성하지 못했습니다.")
        return outputs[0]
