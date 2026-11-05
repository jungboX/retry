from __future__ import annotations

import json
from typing import Any


WEIGHTS = {
    "date": 30,
    "workType": 25,
    "distance": 20,
    "experience": 15,
    "pay": 10,
}


def calculate_score(job: dict[str, Any], worker: dict[str, Any]) -> dict[str, Any]:
    reasons: list[str] = []

    date_match = worker["availableStart"] <= job["startDate"] and worker["availableEnd"] >= job["endDate"]
    date_score = WEIGHTS["date"] if date_match else 0
    reasons.append("일정이 요청 기간과 일치합니다." if date_match else "가능 날짜가 맞지 않습니다.")

    requested_work = "".join(job["workType"].split()).lower()
    supported_work_types = (
        "".join(work_type.split()).lower()
        for work_type in worker.get("workTypes", [])
    )
    work_match = any(
        requested_work in work_type or work_type in requested_work
        for work_type in supported_work_types
    )
    work_score = WEIGHTS["workType"] if work_match else 0
    reasons.append("작업 경험이 일치합니다." if work_match else "작업 경험을 확인해야 합니다.")

    distance_score = max(0, WEIGHTS["distance"] - max(0, worker["distanceKm"] - 5))
    reasons.append(
        "이동 거리가 가깝습니다." if distance_score else "이동 거리를 확인해야 합니다."
    )

    experience_score = min(WEIGHTS["experience"], worker["experienceYears"] * 3)
    reasons.append(
        "농작업 경험이 있습니다." if experience_score else "경험 수준을 확인해야 합니다."
    )

    pay_match = job["dailyPay"] >= worker["minimumPay"]
    pay_score = WEIGHTS["pay"] if pay_match else 0
    reasons.append("희망 일급 조건을 충족합니다." if pay_match else "희망 일급과 차이가 있습니다.")

    weights = {
        "date": date_score,
        "workType": work_score,
        "distance": distance_score,
        "experience": experience_score,
        "pay": pay_score,
    }
    total = sum(weights.values())
    return {
        "workerId": worker.get("id"),
        "score": total,
        "reasons": reasons,
        "weights": weights,
    }


if __name__ == "__main__":
    sample_job = {
        "workType": "복숭아 적과",
        "startDate": "2026-11-09",
        "endDate": "2026-11-11",
        "dailyPay": 120000,
    }

    sample_worker = {
        "id": "worker-01",
        "distanceKm": 5,
        "experienceYears": 3,
        "minimumPay": 100000,
        "workTypes": ["복숭아 적과", "과수 작업"],
        "availableStart": "2026-11-09",
        "availableEnd": "2026-11-11",
    }

    print(json.dumps(calculate_score(sample_job, sample_worker), ensure_ascii=False, indent=2))
