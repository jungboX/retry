package com.retry.api.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class JobsController {

    @PostMapping("/jobs/analyze")
    public Map<String, Object> analyze(@RequestBody JobAnalysisRequest request) {
        return Map.of(
            "workType", "복숭아 적과",
            "location", "청도군 매전면",
            "startDate", "2026-11-09",
            "endDate", "2026-11-11",
            "workersNeeded", 3,
            "dailyPay", 120000,
            "mealProvided", true,
            "missingFields", List.of(),
            "hazardFlags", List.of("안전 장비 점검")
        );
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createJob(@RequestBody JobCreateRequest request) {
        return Map.of(
            "id", "job-001",
            "status", "CANDIDATES_READY",
            "workType", request.workType(),
            "location", request.location()
        );
    }

    @GetMapping("/jobs/{jobId}/matches")
    public List<Map<String, Object>> matches(@PathVariable String jobId) {
        return List.of(
            Map.of(
                "id", "match-001",
                "jobId", jobId,
                "workerId", "worker-01",
                "totalScore", 87,
                "scoreReasons", List.of("근거리", "경험 적합", "일급 기준 충족"),
                "farmerApproved", false,
                "workerAccepted", false,
                "status", "CANDIDATES_READY"
            )
        );
    }

    @PostMapping("/matches/{matchId}/farmer-approve")
    public Map<String, Object> farmerApprove(@PathVariable String matchId) {
        return Map.of("matchId", matchId, "farmerApproved", true, "status", "FARMER_APPROVED");
    }

    @PostMapping("/matches/{matchId}/worker-accept")
    public Map<String, Object> workerAccept(@PathVariable String matchId) {
        return Map.of("matchId", matchId, "workerAccepted", true, "status", "CONFIRMED");
    }

    @GetMapping("/demo/state")
    public Map<String, Object> demoState() {
        return Map.of(
            "jobId", "job-001",
            "matchId", "match-001",
            "isFarmerApproved", false,
            "isWorkerAccepted", false,
            "status", "CANDIDATES_READY"
        );
    }

    public record JobAnalysisRequest(String rawRequest) {}

    public record JobCreateRequest(
        String workType,
        String location,
        String startDate,
        String endDate,
        Integer workersNeeded,
        Integer dailyPay,
        Boolean mealProvided,
        List<String> hazardFlags
    ) {}
}
