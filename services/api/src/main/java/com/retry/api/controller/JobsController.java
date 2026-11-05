package com.retry.api.controller;

import com.retry.api.service.AgentClient;
import com.retry.api.service.DemoSessionStore;
import com.retry.api.service.DemoSessionStore.DemoState;
import com.retry.api.service.DemoSessionStore.Match;
import com.retry.api.service.DemoSessionStore.PendingAnalysis;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class JobsController {
    private final AgentClient agentClient;
    private final DemoSessionStore sessions;

    public JobsController(AgentClient agentClient, DemoSessionStore sessions) {
        this.agentClient = agentClient;
        this.sessions = sessions;
    }

    @PostMapping("/jobs/analyze")
    public Map<String, Object> analyze(
        @RequestHeader("X-Demo-Session-Id") UUID sessionId,
        @Valid @RequestBody JobAnalysisRequest request
    ) {
        Map<String, Object> analysis = agentClient.analyze(request.rawRequest());
        String analysisId = sessions.saveAnalysis(sessionId, request.rawRequest(), analysis);
        return Map.of("analysisId", analysisId, "analysis", analysis);
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createJob(
        @RequestHeader("X-Demo-Session-Id") UUID sessionId,
        @Valid @RequestBody JobCreateRequest request
    ) {
        return sessions.createJob(sessionId, request.analysisId());
    }

    @GetMapping("/jobs/{jobId}/matches")
    public List<Match> matches(
        @RequestHeader("X-Demo-Session-Id") UUID sessionId,
        @PathVariable String jobId
    ) {
        return sessions.matches(sessionId, jobId);
    }

    @PostMapping("/jobs/{jobId}/safety-confirm")
    public Map<String, Object> confirmSafety(
        @RequestHeader("X-Demo-Session-Id") UUID sessionId,
        @PathVariable String jobId,
        @Valid @RequestBody SafetyConfirmation request
    ) {
        return sessions.confirmSafety(sessionId, jobId, request.confirmed());
    }

    @PostMapping("/matches/{matchId}/farmer-approve")
    public Match farmerApprove(
        @RequestHeader("X-Demo-Session-Id") UUID sessionId,
        @PathVariable String matchId
    ) {
        return sessions.farmerApprove(sessionId, matchId);
    }

    @PostMapping("/matches/{matchId}/worker-accept")
    public Match workerAccept(
        @RequestHeader("X-Demo-Session-Id") UUID sessionId,
        @PathVariable String matchId
    ) {
        return sessions.workerAccept(sessionId, matchId);
    }

    @GetMapping("/demo/state")
    public DemoState demoState(@RequestHeader("X-Demo-Session-Id") UUID sessionId) {
        return sessions.getState(sessionId);
    }

    public record JobAnalysisRequest(@NotBlank String rawRequest) {}

    public record JobCreateRequest(@NotBlank String analysisId) {}

    public record SafetyConfirmation(@NotNull Boolean confirmed) {}
}
