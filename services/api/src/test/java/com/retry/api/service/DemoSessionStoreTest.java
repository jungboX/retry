package com.retry.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

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
        store = new DemoSessionStore(jdbc, JsonMapper.builder().findAndAddModules().build());
    }

    @Test
    void confirmsOnlyAfterBothPartiesApprove() {
        UUID sessionId = UUID.randomUUID();
        String analysisId = store.saveAnalysis(sessionId, analysis(List.of(), List.of()));
        Map<String, Object> created = store.createJob(sessionId, analysisId);
        @SuppressWarnings("unchecked")
        List<DemoSessionStore.Match> matches = (List<DemoSessionStore.Match>) created.get("matches");
        DemoSessionStore.Match candidate = matches.get(0);

        assertEquals("FARMER_APPROVED", store.farmerApprove(sessionId, candidate.id()).status());
        assertEquals("CONFIRMED", store.workerAccept(sessionId, candidate.id()).status());
    }

    @Test
    void rejectsWorkerAcceptanceBeforeFarmerApproval() {
        UUID sessionId = UUID.randomUUID();
        String analysisId = store.saveAnalysis(sessionId, analysis(List.of(), List.of()));
        store.createJob(sessionId, analysisId);
        DemoSessionStore.Match match = store.matches(sessionId, store.getState(sessionId).getJobId()).get(0);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> store.workerAccept(sessionId, match.id())
        );

        assertEquals(409, exception.getStatusCode().value());
        assertFalse(store.matches(sessionId, match.jobId()).get(0).workerAccepted());
    }

    @Test
    void restoresFarmerApprovalWhenPersistenceFails() {
        UUID sessionId = UUID.randomUUID();
        String analysisId = store.saveAnalysis(sessionId, analysis(List.of(), List.of()));
        store.createJob(sessionId, analysisId);

        JdbcTemplate failingJdbc = spy(new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "sa", "")));
        DemoSessionStore failingStore = new DemoSessionStore(
            failingJdbc,
            JsonMapper.builder().findAndAddModules().build()
        );
        DemoSessionStore.DemoState state = failingStore.getState(sessionId);
        DemoSessionStore.Match match = failingStore.matches(sessionId, state.getJobId()).get(0);

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("UPDATE demo_sessions SET state_json")) {
                throw new DataAccessResourceFailureException("Simulated database failure");
            }
            return invocation.callRealMethod();
        }).when(failingJdbc).update(anyString(), any(Object[].class));

        assertThrows(
            DataAccessResourceFailureException.class,
            () -> failingStore.farmerApprove(sessionId, match.id())
        );

        reset(failingJdbc);
        assertFalse(failingStore.matches(sessionId, match.jobId()).get(0).farmerApproved());
        assertFalse(store.matches(sessionId, match.jobId()).get(0).farmerApproved());
    }

    @Test
    void restoresWorkerAcceptanceWhenPersistenceFails() {
        UUID sessionId = UUID.randomUUID();
        String analysisId = store.saveAnalysis(sessionId, analysis(List.of(), List.of()));
        store.createJob(sessionId, analysisId);
        DemoSessionStore.Match match = store.matches(sessionId, store.getState(sessionId).getJobId()).get(0);
        store.farmerApprove(sessionId, match.id());

        JdbcTemplate failingJdbc = spy(new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "sa", "")));
        DemoSessionStore failingStore = new DemoSessionStore(
            failingJdbc,
            JsonMapper.builder().findAndAddModules().build()
        );
        DemoSessionStore.DemoState state = failingStore.getState(sessionId);
        DemoSessionStore.Match approvedMatch = failingStore.matches(sessionId, state.getJobId()).get(0);

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("UPDATE demo_sessions SET state_json")) {
                throw new DataAccessResourceFailureException("Simulated database failure");
            }
            return invocation.callRealMethod();
        }).when(failingJdbc).update(anyString(), any(Object[].class));

        assertThrows(
            DataAccessResourceFailureException.class,
            () -> failingStore.workerAccept(sessionId, approvedMatch.id())
        );

        reset(failingJdbc);
        DemoSessionStore.Match afterFailure = failingStore.matches(sessionId, approvedMatch.jobId()).get(0);
        assertTrue(afterFailure.farmerApproved());
        assertFalse(afterFailure.workerAccepted());
        assertEquals("FARMER_APPROVED", afterFailure.status());
        assertEquals("FARMER_APPROVED", store.matches(sessionId, approvedMatch.jobId()).get(0).status());
    }

    @Test
    void isolatesDemoStateBetweenSessions() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String analysisId = store.saveAnalysis(first, analysis(List.of(), List.of()));
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
        String analysisId = store.saveAnalysis(sessionId, analysis(List.of("농기계"), List.of()));
        store.createJob(sessionId, analysisId);
        DemoSessionStore.Match match = store.matches(sessionId, store.getState(sessionId).getJobId()).get(0);

        assertThrows(ResponseStatusException.class, () -> store.farmerApprove(sessionId, match.id()));
        store.confirmSafety(sessionId, match.jobId(), true);
        assertEquals("FARMER_APPROVED", store.farmerApprove(sessionId, match.id()).status());
    }

    @Test
    void refusesToCreateJobWhenRequiredConditionsAreMissing() {
        UUID sessionId = UUID.randomUUID();
        String analysisId = store.saveAnalysis(sessionId, Map.of(
            "job", Map.of("missingFields", List.of("dailyPay")),
            "missingFields", List.of("dailyPay"),
            "recommendedWorkers", List.of()
        ));

        assertThrows(ResponseStatusException.class, () -> store.createJob(sessionId, analysisId));
    }

    @Test
    void restoresSessionStateFromDatabase() {
        UUID sessionId = UUID.randomUUID();
        String analysisId = store.saveAnalysis(sessionId, analysis(List.of(), List.of()));
        store.createJob(sessionId, analysisId);
        String jobId = store.getState(sessionId).getJobId();

        var reopenedStore = new DemoSessionStore(
            new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "sa", "")),
            JsonMapper.builder().findAndAddModules().build()
        );

        assertEquals(jobId, reopenedStore.getState(sessionId).getJobId());
        assertEquals(1, reopenedStore.matches(sessionId, jobId).size());
    }

    @Test
    void rejectsNonPositiveJobConditions() {
        UUID sessionId = UUID.randomUUID();
        Map<String, Object> analysis = new java.util.HashMap<>(analysis(List.of(), List.of()));
        Map<String, Object> job = new java.util.HashMap<>(objectMap(analysis.get("job")));
        job.put("workersNeeded", 0);
        analysis.put("job", job);
        String analysisId = store.saveAnalysis(sessionId, analysis);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> store.createJob(sessionId, analysisId)
        );

        assertEquals(422, exception.getStatusCode().value());
    }

    @Test
    void doesNotAllowSafetyConfirmationToBeRevokedAfterFarmerApproval() {
        UUID sessionId = UUID.randomUUID();
        String analysisId = store.saveAnalysis(sessionId, analysis(List.of("농기계"), List.of()));
        Map<String, Object> created = store.createJob(sessionId, analysisId);
        @SuppressWarnings("unchecked")
        List<DemoSessionStore.Match> matches = (List<DemoSessionStore.Match>) created.get("matches");
        DemoSessionStore.Match match = matches.get(0);
        store.confirmSafety(sessionId, match.jobId(), true);
        store.farmerApprove(sessionId, match.id());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> store.confirmSafety(sessionId, match.jobId(), false)
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals("CONFIRMED", store.workerAccept(sessionId, match.id()).status());
    }

    @Test
    void reportsEmptyJobAsNullInDemoState() {
        assertNull(store.getState(UUID.randomUUID()).getJob());
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

    private static Map<String, Object> objectMap(Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }
}
