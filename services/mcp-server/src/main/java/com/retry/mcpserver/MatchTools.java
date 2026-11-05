package com.retry.mcpserver;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MatchTools {

    public List<Map<String, Object>> findCandidateWorkers(String location, String workType, String startDate, String endDate) {
        return List.of(
            Map.of(
                "id", "worker-01",
                "nickname", "청도딸기농부",
                "homeLocation", location,
                "availableStart", startDate,
                "availableEnd", endDate,
                "workTypes", List.of(workType),
                "experienceLevel", "2년",
                "travelRadiusKm", 20
            )
        );
    }

    public Map<String, Object> calculateMatchScore(Map<String, Object> worker, Map<String, Object> job) {
        return Map.of(
            "workerId", worker.get("id"),
            "totalScore", 87,
            "reasons", List.of("근거리", "일정 적합", "작업 유형 일치"),
            "weights", Map.of("distance", 30, "skill", 25, "schedule", 20, "safety", 25)
        );
    }

    public Map<String, Object> checkJobSafety(String workType, List<String> hazardFlags) {
        return Map.of(
            "workType", workType,
            "riskLevel", "MEDIUM",
            "hazardFlags", hazardFlags,
            "requiredChecks", List.of("안전 장비 확인", "작업 전 교육", "고온/고강도 위험 점검")
        );
    }

    public Map<String, Object> getMatchDetails(String workerId) {
        return Map.of(
            "workerId", workerId,
            "scoreReasons", List.of("근거리", "기술 수준", "기간 충족"),
            "notes", "안전 교육 이수 상태가 양호합니다."
        );
    }
}
