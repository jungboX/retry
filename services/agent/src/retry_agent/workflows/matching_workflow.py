from __future__ import annotations

from typing import Any


class MatchingWorkflow:
    """JobRegistrationAgent -> SafetyReviewAgent -> MatchingAgent -> ExplanationAgent."""

    def run(self, parsed_job: dict[str, Any]) -> dict[str, Any]:
        return {
            "job": parsed_job,
            "status": "CANDIDATES_READY",
            "recommendedWorkers": [
                {
                    "workerId": "worker-01",
                    "score": 87,
                    "reason": "근거리에 위치하고 작업 경험이 적합합니다.",
                }
            ],
            "explanation": "안전 점검을 전제로 근거리 구직자 1명이 가장 적합합니다.",
        }
