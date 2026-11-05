from calculate_match_score import calculate_score


def test_score_matches_documented_weights_and_mcp_worker() -> None:
    job = {
        "workType": "복숭아 적과",
        "startDate": "2026-11-09",
        "endDate": "2026-11-11",
        "dailyPay": 120000,
    }
    worker = {
        "id": "worker-01",
        "distanceKm": 5,
        "experienceYears": 3,
        "minimumPay": 100000,
        "workTypes": ["복숭아 적과", "과수 작업"],
        "availableStart": "2026-01-01",
        "availableEnd": "2027-12-31",
    }

    result = calculate_score(job, worker)

    assert result["score"] == 94
    assert result["weights"] == {
        "date": 30,
        "workType": 25,
        "distance": 20,
        "experience": 9,
        "pay": 10,
    }


def test_score_uses_documented_mismatches() -> None:
    job = {
        "workType": "복숭아 적과",
        "startDate": "2026-11-09",
        "endDate": "2026-11-11",
        "dailyPay": 90000,
    }
    worker = {
        "id": "worker-02",
        "distanceKm": 40,
        "experienceYears": 8,
        "minimumPay": 100000,
        "workTypes": ["수확 보조"],
        "availableStart": "2026-11-10",
        "availableEnd": "2026-11-11",
    }

    result = calculate_score(job, worker)

    assert result["score"] == 15
    assert result["weights"] == {
        "date": 0,
        "workType": 0,
        "distance": 0,
        "experience": 15,
        "pay": 0,
    }
