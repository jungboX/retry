package com.retry.mcpserver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

@Service
public class MatchTools {
    private static final List<Worker> WORKERS = List.of(
        new Worker("worker-01", "복숭아 도우미", 5, 3, 100_000, List.of("복숭아 적과", "과수 작업"),
            LocalDate.parse("2026-01-01"), LocalDate.parse("2027-12-31")),
        new Worker("worker-02", "청도 농작업 지원자", 12, 4, 110_000, List.of("복숭아 적과", "수확 보조"),
            LocalDate.parse("2026-01-01"), LocalDate.parse("2027-12-31")),
        new Worker("worker-03", "과수 작업 지원자", 18, 2, 120_000, List.of("과수 작업", "수확 보조"),
            LocalDate.parse("2026-01-01"), LocalDate.parse("2027-12-31")),
        new Worker("worker-04", "농작업 데모 구직자", 30, 5, 100_000, List.of("복숭아 적과"),
            LocalDate.parse("2026-01-01"), LocalDate.parse("2027-12-31"))
    );

    @McpTool(name = "find_candidate_workers", description = "요청 날짜, 지역, 작업에 맞는 데모 구직자를 찾습니다.")
    public List<Map<String, Object>> findCandidateWorkers(
        @McpToolParam(description = "작업 지역", required = true) String location,
        @McpToolParam(description = "작업 종류", required = true) String workType,
        @McpToolParam(description = "시작일 ISO 날짜", required = true) String startDate,
        @McpToolParam(description = "종료일 ISO 날짜", required = true) String endDate,
        @McpToolParam(description = "필요 인원", required = true) int workersNeeded,
        @McpToolParam(description = "일급 원화", required = true) int dailyPay
    ) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        if (workersNeeded < 1 || dailyPay < 1 || end.isBefore(start)) {
            throw new IllegalArgumentException("Invalid job conditions");
        }
        if (!location.contains("청도")) {
            return List.of();
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Worker worker : WORKERS) {
            if (worker.availableStart().isAfter(start) || worker.availableEnd().isBefore(end)) {
                continue;
            }
            if (workMatch(worker.workTypes(), workType) == 0) {
                continue;
            }
            candidates.add(worker.toMap(location));
        }
        return candidates;
    }

    @McpTool(name = "calculate_match_score", description = "날짜, 작업, 이동 거리, 경험, 임금의 고정 가중치로 점수를 계산합니다.")
    public Map<String, Object> calculateMatchScore(
        @McpToolParam(description = "데모 구직자 ID", required = true) String workerId,
        @McpToolParam(description = "작업 종류", required = true) String workType,
        @McpToolParam(description = "작업 시작일 ISO 날짜", required = true) String startDate,
        @McpToolParam(description = "작업 종료일 ISO 날짜", required = true) String endDate,
        @McpToolParam(description = "요청 일급 원화", required = true) int dailyPay
    ) {
        Worker worker = worker(workerId);
        boolean dateMatch = !worker.availableStart().isAfter(LocalDate.parse(startDate))
            && !worker.availableEnd().isBefore(LocalDate.parse(endDate));
        int dateScore = dateMatch ? 30 : 0;
        int workScore = workMatch(worker.workTypes(), workType);
        int distanceScore = Math.max(0, 20 - Math.max(0, worker.distanceKm() - 5));
        int experienceScore = Math.min(15, worker.experienceYears() * 3);
        int payScore = dailyPay >= worker.minimumPay() ? 10 : 0;
        int total = dateScore + workScore + distanceScore + experienceScore + payScore;

        List<String> reasons = new ArrayList<>();
        addReason(reasons, dateScore, "일정이 요청 기간과 일치합니다.", "가능 날짜가 맞지 않습니다.");
        addReason(reasons, workScore, "작업 경험이 일치합니다.", "작업 경험을 확인해야 합니다.");
        addReason(reasons, distanceScore, "이동 거리가 가깝습니다.", "이동 거리를 확인해야 합니다.");
        addReason(reasons, experienceScore, "농작업 경험이 있습니다.", "경험 수준을 확인해야 합니다.");
        addReason(reasons, payScore, "희망 일급 조건을 충족합니다.", "희망 일급과 차이가 있습니다.");
        return Map.of(
            "workerId", workerId,
            "totalScore", total,
            "reasons", List.copyOf(reasons),
            "weights", Map.of(
                "date", dateScore,
                "workType", workScore,
                "distance", distanceScore,
                "experience", experienceScore,
                "pay", payScore
            )
        );
    }

    @McpTool(name = "check_job_safety", description = "작업 요청의 위험 요소와 사용자 확인 항목을 반환합니다.")
    public Map<String, Object> checkJobSafety(
        @McpToolParam(description = "작업 종류", required = true) String workType,
        @McpToolParam(description = "요청에서 확인된 위험 문구") List<String> hazardFlags
    ) {
        List<String> flags = new ArrayList<>(hazardFlags == null ? List.of() : hazardFlags);
        String text = (workType + " " + String.join(" ", flags)).toLowerCase();
        List<String> checks = new ArrayList<>();
        addSafetyCheck(text, checks, List.of("농약", "농약살포", "살충제"), "보호구 착용과 취급 교육을 확인해 주세요.");
        addSafetyCheck(text, checks, List.of("농기계", "트랙터", "예초기", "기계"), "장비 점검과 작업 전 안전 교육을 확인해 주세요.");
        addSafetyCheck(text, checks, List.of("고소", "사다리", "높은 곳"), "추락 방지 장비와 작업 높이를 확인해 주세요.");
        boolean confirmationRequired = !checks.isEmpty();
        return Map.of(
            "workType", workType,
            "riskLevel", confirmationRequired ? "REVIEW_REQUIRED" : "STANDARD",
            "hazardFlags", List.copyOf(flags),
            "requiredChecks", List.copyOf(checks),
            "confirmationRequired", confirmationRequired
        );
    }

    @McpTool(name = "get_match_details", description = "추천 후보의 데모 정보와 확인할 조건을 조회합니다.")
    public Map<String, Object> getMatchDetails(
        @McpToolParam(description = "데모 구직자 ID", required = true) String workerId
    ) {
        Worker worker = worker(workerId);
        return Map.of(
            "workerId", worker.id(),
            "nickname", worker.nickname(),
            "experienceYears", worker.experienceYears(),
            "travelDistanceKm", worker.distanceKm(),
            "workTypes", worker.workTypes(),
            "notes", "데모 구직자 정보이며 실제 인물이나 연락처가 아닙니다."
        );
    }

    private static Worker worker(String id) {
        return WORKERS.stream().filter(candidate -> candidate.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown demo worker"));
    }

    private static int workMatch(List<String> workerTypes, String requested) {
        String normalized = requested.replaceAll("\\s+", "").toLowerCase();
        return workerTypes.stream().anyMatch(type ->
            normalized.contains(type.replaceAll("\\s+", "").toLowerCase())
                || type.replaceAll("\\s+", "").toLowerCase().contains(normalized)
        ) ? 25 : 0;
    }

    private static void addReason(List<String> reasons, int score, String positive, String negative) {
        reasons.add(score > 0 ? positive : negative);
    }

    private static void addSafetyCheck(String text, List<String> checks, List<String> keywords, String check) {
        if (keywords.stream().anyMatch(text::contains)) {
            checks.add(check);
        }
    }

    private record Worker(
        String id,
        String nickname,
        int distanceKm,
        int experienceYears,
        int minimumPay,
        List<String> workTypes,
        LocalDate availableStart,
        LocalDate availableEnd
    ) {
        Map<String, Object> toMap(String location) {
            return Map.of(
                "id", id,
                "nickname", nickname,
                "homeLocation", location,
                "distanceKm", distanceKm,
                "experienceYears", experienceYears,
                "minimumPay", minimumPay,
                "workTypes", workTypes,
                "availableStart", availableStart.toString(),
                "availableEnd", availableEnd.toString()
            );
        }
    }
}
