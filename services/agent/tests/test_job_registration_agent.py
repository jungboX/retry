import pytest

from retry_agent.agents.job_registration_agent import JobRegistrationAgent


def test_extracts_valid_json_and_adds_missing_required_conditions() -> None:
    parsed = JobRegistrationAgent._parse_json(
        '```json\n{"workType":"복숭아 적과","location":"청도군 매전면",'
        '"startDate":"2026-11-09","endDate":"2026-11-11","workersNeeded":2,'
        '"dailyPay":120000,"mealProvided":true,"hazardFlags":[],"missingFields":[]}\n```'
    )

    result = JobRegistrationAgent._validate(parsed)

    assert result["missingFields"] == []
    assert result["workersNeeded"] == 2
    assert result["dailyPay"] == 120000


def test_does_not_invent_missing_required_values() -> None:
    result = JobRegistrationAgent._validate({
        "workType": "복숭아 적과",
        "location": None,
        "startDate": None,
        "endDate": None,
        "workersNeeded": None,
        "dailyPay": None,
        "hazardFlags": [],
        "missingFields": ["location"],
    })

    assert result["location"] is None
    assert set(result["missingFields"]) == {"location", "startDate", "endDate", "workersNeeded", "dailyPay"}


@pytest.mark.parametrize(
    "field,value",
    [("workersNeeded", 0), ("workersNeeded", True), ("dailyPay", -1)],
)
def test_rejects_invalid_positive_integer_fields(field: str, value: object) -> None:
    result = {
        "workType": "복숭아 적과",
        "location": "청도군 매전면",
        "startDate": "2026-11-09",
        "endDate": "2026-11-11",
        "workersNeeded": 2,
        "dailyPay": 120000,
        "hazardFlags": [],
        "missingFields": [],
    }
    result[field] = value

    with pytest.raises(ValueError):
        JobRegistrationAgent._validate(result)


def test_rejects_end_date_before_start_date() -> None:
    with pytest.raises(ValueError):
        JobRegistrationAgent._validate({
            "workType": "복숭아 적과",
            "location": "청도군 매전면",
            "startDate": "2026-11-12",
            "endDate": "2026-11-11",
            "workersNeeded": 2,
            "dailyPay": 120000,
            "hazardFlags": [],
            "missingFields": [],
        })


def test_rejects_non_json_model_response() -> None:
    with pytest.raises(ValueError):
        JobRegistrationAgent._parse_json("분석 결과입니다.")
