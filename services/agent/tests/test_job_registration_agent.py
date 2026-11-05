from datetime import date

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


def test_fallback_parser_extracts_basic_demo_request() -> None:
    parsed = JobRegistrationAgent._fallback_parse(
        "청도군 매전면에서 11월 9일부터 11월 11일까지 복숭아 적과 작업 2명, 일급 120000원, 식사 제공, 농기계 안전 점검"
    )

    assert parsed["location"] == "청도군 매전면"
    assert parsed["workType"] == "복숭아 적과"
    assert parsed["workersNeeded"] == 2
    assert parsed["dailyPay"] == 120000
    assert parsed["mealProvided"] is True
    assert parsed["hazardFlags"] == ["농기계", "안전 점검"]


def test_fallback_parser_handles_prd_relative_weekdays_and_korean_pay() -> None:
    parsed = JobRegistrationAgent._fallback_parse(
        "다음 주 월요일부터 수요일까지 청도 매전면 복숭아 적과 작업에 3명이 필요해요. "
        "하루 12만 원이고 점심을 제공합니다."
    )

    assert parsed["location"] == "청도 매전면"
    assert parsed["startDate"] == "2026-11-09"
    assert parsed["endDate"] == "2026-11-11"
    assert parsed["workersNeeded"] == 3
    assert parsed["dailyPay"] == 120000


@pytest.mark.parametrize(
    "text,expected_dates",
    [
        ("2026년 11월 9일부터 11일까지", ("2026-11-09", "2026-11-11")),
        ("2026년 11월 9일부터 11월 11일까지", ("2026-11-09", "2026-11-11")),
    ],
)
def test_extracts_explicit_date_ranges(text: str, expected_dates: tuple[str, str]) -> None:
    start_date, end_date = JobRegistrationAgent._extract_dates(text, date(2026, 11, 5))

    assert (start_date.isoformat(), end_date.isoformat()) == expected_dates


@pytest.mark.parametrize(
    "text,expected_pay",
    [
        ("하루 12만 원", 120000),
        ("하루 12만 5천원", 125000),
        ("일급 120,000원", 120000),
    ],
)
def test_fallback_parser_extracts_korean_and_numeric_pay(text: str, expected_pay: int) -> None:
    parsed = JobRegistrationAgent._fallback_parse(text)

    assert parsed["dailyPay"] == expected_pay


@pytest.mark.parametrize("field", ["workType", "location"])
def test_rejects_non_string_job_labels(field: str) -> None:
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
    result[field] = 123

    with pytest.raises(ValueError, match=f"{field} must be a string"):
        JobRegistrationAgent._validate(result)


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
