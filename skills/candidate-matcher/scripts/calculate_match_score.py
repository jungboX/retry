from __future__ import annotations

import json
from typing import Any


WEIGHTS = {
    "distance": 30,
    "skill": 25,
    "schedule": 20,
    "safety": 25,
}


def calculate_score(job: dict[str, Any], worker: dict[str, Any]) -> dict[str, Any]:
    reasons: list[str] = []
    score = 0

    if worker.get("homeLocation") == job.get("location"):
        score += WEIGHTS["distance"]
        reasons.append("근거리에 위치해 이동 부담이 낮습니다.")
    else:
        reasons.append("근거리 조건은 다소 아쉽지만, 다른 항목이 우수합니다.")

    if job.get("workType") in worker.get("workTypes", []):
        score += WEIGHTS["skill"]
        reasons.append("작업 유형과 경험이 적합합니다.")
    else:
        reasons.append("작업 유형이 완전히 일치하지는 않습니다.")

    if worker.get("availableStart") <= job.get("startDate") and worker.get("availableEnd") >= job.get("endDate"):
        score += WEIGHTS["schedule"]
        reasons.append("근무 일정이 작업 기간과 일치합니다.")
    else:
        reasons.append("근무 일정이 작업 기간과 정확히 맞지 않습니다.")

    if "안전 장비 점검" in job.get("hazardFlags", []):
        score += WEIGHTS["safety"]
        reasons.append("안전 장비 확인이 가능해 작업 위험 대응이 좋습니다.")
    else:
        reasons.append("안전 항목은 기본 수준으로 평가됩니다.")

    total = min(score, 100)
    return {
        "workerId": worker.get("id"),
        "score": total,
        "reasons": reasons,
    }


if __name__ == "__main__":
    sample_job = {
        "workType": "복숭아 적과",
        "location": "청도군 매전면",
        "startDate": "2026-11-09",
        "endDate": "2026-11-11",
        "hazardFlags": ["안전 장비 점검"],
    }

    sample_worker = {
        "id": "worker-01",
        "homeLocation": "청도군 매전면",
        "workTypes": ["복숭아 적과", "과수 작업"],
        "availableStart": "2026-11-09",
        "availableEnd": "2026-11-11",
    }

    print(json.dumps(calculate_score(sample_job, sample_worker), ensure_ascii=False, indent=2))
