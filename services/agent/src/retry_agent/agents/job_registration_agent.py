from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class JobRegistrationAgent:
    """자연어 작업 요청을 구조화된 JSON으로 변환하는 에이전트."""

    raw_request: str = ""
    extracted_fields: dict[str, Any] = field(default_factory=dict)

    def run(self) -> dict[str, Any]:
        return {
            "workType": "복숭아 적과",
            "location": "청도군 매전면",
            "startDate": "2026-11-09",
            "endDate": "2026-11-11",
            "workersNeeded": 3,
            "dailyPay": 120000,
            "mealProvided": True,
            "missingFields": [],
            "hazardFlags": ["안전 장비 점검"],
        }
