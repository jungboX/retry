package com.retry.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

class DemoSessionStoreTest {
    private DemoSessionStore store;
    private String databaseUrl;

    @BeforeEach
    void setUp() {
        databaseUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        var dataSource = new DriverManagerDataSource(databaseUrl, "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
            CREATE TABLE demo_sessions (
                id UUID PRIMARY KEY,
                state_json TEXT NOT NULL,
                last_access TIMESTAMP WITH TIME ZONE NOT NULL
            )
            """);
        store = new DemoSessionStore(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void confirmsOnlyAfterBothPartiesApprove() {
        UUID sessionId = UUID.randomUUID();
        String analysisId = store.saveAnalysis(sessionId, "복숭아 적과", analysis(List.of(), List.of()));
        Map<String, Object> created = store.createJob(sessionId, analysisId);
        @SuppressWarnings("unchecked")
        List<DemoSessionStore.Match> matches = (List<DemoSessionStore.Match>) created.get("matches");
        DemoSessionStore.Match candidate = matches.getFirst();

        assertEquals("FARMER_APPROVED", store.farmerApprove(sessionId, candidate.id()).status());
        assertEquals("CONFIRMED", store.workerAccept(sessionId, candidate.id()).status());
    }

    @Test
    void rejectsWorkerAcceptanceBeforeFarmerApproval() {
        UUID sessionId = UUID.randomUUID();
        String analysisId = store.saveAnalysis(sessionId, "복숭아 적과", analysis(List.of(), List.of()));
        store.createJob(sessionId, analysisId);
        DemoSessionStore.Match match = store.matches(sessionId, store.getState(sessionId).getJobId()).getFirst();

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> store.workerAccept(sessionId, match.id())
        );

        assertEquals(409, exception.getStatusCode().value());
        assertFalse(store.matches(sessionId, match.jobId()).getFirst().workerAccepted());
    }

    @Test
    void isolatesDemoStateBetweenSessions() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String analysisId = store.saveAnalysis(first, "요청", analysis(List.of(), List.of()));
        store.createJob(first, analysisId);

        assertTrue(store.getState(first).getJobId() != null);
        assertTrue(store.getState(second).getJobId() == null);
        assertThrows(
            ResponseStatusException.class,
            () -> store.matches(second, store.getState(first).getJobId())
        );
    }

    @Test
    void requiresSafetyAcknowledgementForHazardousJobs() {
        UUID sessionId = UUID.randomUUID();
        String analysisId = store.saveAnalysis(sessionId, "농기계 작업", analysis(List.of("농기계"), List.of()));
        store.createJob(sessionId, analysisId);
        DemoSessionStore.Match match = store.matches(sessionId, store.getState(sessionId).getJobId()).getFirst();

        assertThrows(ResponseStatusException.class, () -> store.farmerApprove(sessionId, match.id()));
        store.confirmSafety(sessionId, match.jobId(), true);
        assertEquals("FARMER_APPROVED", store.farmerApprove(sessionId, match.id()).status());
    }

    @Test
    void refusesToCreateJobWhenRequiredConditionsAreMissing() {
        UUID sessionId = UUID.randomUUID();
        String analysisId = store.saveAnalysis(sessionId, "조건이 빠진 요청", Map.of(
            "job", Map.of("missingFields", List.of("dailyPay")),
            "missingFields", List.of("dailyPay"),
            "recommendedWorkers", List.of()
        ));

        assertThrows(ResponseStatusException.class, () -> store.createJob(sessionId, analysisId));
    }

    @Test
    void restoresSessionStateFromDatabase() {
        UUID sessionId = UUID.randomUUID();
        String analysisId = store.saveAnalysis(sessionId, "복숭아 적과", analysis(List.of(), List.of()));
        store.createJob(sessionId, analysisId);
        String jobId = store.getState(sessionId).getJobId();

        var reopenedStore = new DemoSessionStore(
            new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "sa", "")),
            new ObjectMapper().findAndRegisterModules()
        );

        assertEquals(jobId, reopenedStore.getState(sessionId).getJobId());
        assertEquals(1, reopenedStore.matches(sessionId, jobId).size());
    }

    private Map<String, Object> analysis(List<String> hazardFlags, List<Map<String, Object>> candidates) {
        Map<String, Object> job = Map.of(
            "workType", "복숭아 적과",
            "location", "청도군 매전면",
            "startDate", "2026-11-09",
            "endDate", "2026-11-11",
            "workersNeeded", 1,
            "dailyPay", 120000,
            "mealProvided", true,
            "hazardFlags", hazardFlags,
            "missingFields", List.of()
        );
        return Map.of(
            "job", job,
            "status", "CANDIDATES_READY",
            "missingFields", List.of(),
            "recommendedWorkers", candidates.isEmpty()
                ? List.of(Map.of("workerId", "worker-01", "nickname", "데모 구직자", "score", 87, "scoreReasons", List.of("일정 일치")))
                : candidates
        );
    }
}
