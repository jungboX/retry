package com.retry.api.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DemoSessionStore {
    private static final long SESSION_TTL_SECONDS = 14_400;
    private static final int MAX_SESSIONS = 1_000;

    private final Map<UUID, DemoState> states = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private final JsonMapper objectMapper;

    public DemoSessionStore(JdbcTemplate jdbc, JsonMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public String saveAnalysis(UUID sessionId, Map<String, Object> analysis) {
        DemoState state = state(sessionId);
        synchronized (state) {
            String analysisId = UUID.randomUUID().toString();
            state.pendingAnalysis = new PendingAnalysis(analysisId, analysis);
            persist(sessionId, state);
            return analysisId;
        }
    }

    public Map<String, Object> createJob(UUID sessionId, String analysisId) {
        DemoState state = state(sessionId);
        synchronized (state) {
            PendingAnalysis pending = state.pendingAnalysis;
            if (pending == null || !pending.id().equals(analysisId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "요청 분석이 만료되었습니다. 다시 분석해 주세요.");
            }
            Map<String, Object> job = objectValue(pending.analysis().get("job"));
            if (job.isEmpty()) {
                job = pending.analysis();
            }
            validateMissingFields(job.get("missingFields"), true);
            validateJob(job);

            String jobId = "job-" + UUID.randomUUID();
            Map<String, Match> candidateMatches = new LinkedHashMap<>();
            Object recommended = pending.analysis().get("recommendedWorkers");
            if (!(recommended instanceof List<?>)) {
                invalidAnalysisResult();
            }
            List<?> candidates = (List<?>) recommended;
            HashSet<String> workerIds = new HashSet<>();
            for (Object candidate : candidates) {
                if (!(candidate instanceof Map<?, ?>)) {
                    invalidAnalysisResult();
                }
                Match match = matchFrom(jobId, (Map<?, ?>) candidate);
                if (!workerIds.add(match.workerId())) {
                    invalidAnalysisResult();
                }
                candidateMatches.put(match.id(), match);
            }

            state.jobId = jobId;
            state.job = Collections.unmodifiableMap(new LinkedHashMap<>(job));
            state.safetyConfirmed = false;
            state.matches.clear();
            state.matches.putAll(candidateMatches);
            state.pendingAnalysis = null;
            persist(sessionId, state);
            return Map.of(
                "id", state.jobId,
                "status", state.matches.isEmpty() ? "NO_CANDIDATES" : "CANDIDATES_READY",
                "job", state.job,
                "matches", List.copyOf(state.matches.values()),
                "safetyConfirmed", state.safetyConfirmed
            );
        }
    }

    public List<Match> matches(UUID sessionId, String jobId) {
        DemoState state = requireJob(sessionId, jobId);
        synchronized (state) {
            state.lastAccess = Instant.now();
            persist(sessionId, state);
            return state.matches.values().stream()
                .sorted(Comparator.comparingInt(Match::totalScore).reversed().thenComparing(Match::id))
                .toList();
        }
    }

    public Map<String, Object> confirmSafety(UUID sessionId, String jobId, boolean confirmed) {
        DemoState state = requireJob(sessionId, jobId);
        synchronized (state) {
            if (confirmed && !hasHazards(state.job)) {
                return Map.of("jobId", jobId, "safetyConfirmed", true, "status", "NO_ADDITIONAL_CHECKS");
            }
            if (!confirmed && state.matches.values().stream().anyMatch(Match::farmerApproved)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "농가 승인된 후보가 있어 안전 확인을 취소할 수 없습니다.");
            }
            state.safetyConfirmed = confirmed;
            persist(sessionId, state);
            return Map.of("jobId", jobId, "safetyConfirmed", confirmed, "status", "UPDATED");
        }
    }

    public Match farmerApprove(UUID sessionId, String matchId) {
        DemoState state = requireMatch(sessionId, matchId);
        synchronized (state) {
            if (hasHazards(state.job) && !state.safetyConfirmed) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "작업 안전 확인을 먼저 완료해 주세요.");
            }
            Match current = state.matches.get(matchId);
            Match updated = current.withFarmerApproved(true);
            state.matches.put(matchId, updated);
            try {
                persist(sessionId, state);
            } catch (RuntimeException exception) {
                state.matches.put(matchId, current);
                throw exception;
            }
            return updated;
        }
    }

    public Match workerAccept(UUID sessionId, String matchId) {
        DemoState state = requireMatch(sessionId, matchId);
        synchronized (state) {
            Match current = state.matches.get(matchId);
            if (!current.farmerApproved()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "농가 승인 후에 수락할 수 있습니다.");
            }
            if (hasHazards(state.job) && !state.safetyConfirmed) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "작업 안전 확인을 먼저 완료해 주세요.");
            }
            Match updated = current.withWorkerAccepted(true);
            state.matches.put(matchId, updated);
            try {
                persist(sessionId, state);
            } catch (RuntimeException exception) {
                state.matches.put(matchId, current);
                throw exception;
            }
            return updated;
        }
    }

    public DemoState getState(UUID sessionId) {
        DemoState state = state(sessionId);
        synchronized (state) {
            persist(sessionId, state);
            return state;
        }
    }

    private DemoState requireJob(UUID sessionId, String jobId) {
        DemoState state = state(sessionId);
        synchronized (state) {
            if (state.jobId == null || !state.jobId.equals(jobId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다.");
            }
        }
        return state;
    }

    private DemoState requireMatch(UUID sessionId, String matchId) {
        DemoState state = state(sessionId);
        synchronized (state) {
            if (!state.matches.containsKey(matchId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "추천 매칭을 찾을 수 없습니다.");
            }
        }
        return state;
    }

    private DemoState state(UUID sessionId) {
        expireOldSessions();
        DemoState state = states.get(sessionId);
        if (state != null) {
            return state;
        }
        synchronized (states) {
            state = states.get(sessionId);
            if (state != null) {
                return state;
            }
            Integer persisted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM demo_sessions WHERE id = ?",
                Integer.class,
                sessionId
            );
            if (persisted == null || persisted == 0) {
                Long activeSessions = jdbc.queryForObject("SELECT COUNT(*) FROM demo_sessions", Long.class);
                if (activeSessions != null && activeSessions >= MAX_SESSIONS) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "현재 데모 이용자가 많습니다. 잠시 후 다시 시도해 주세요.");
                }
            }
            state = load(sessionId);
            states.put(sessionId, state);
            return state;
        }
    }

    private void expireOldSessions() {
        Instant cutoff = Instant.now().minusSeconds(SESSION_TTL_SECONDS);
        states.entrySet().removeIf(entry -> entry.getValue().lastAccess.isBefore(cutoff));
        jdbc.update("DELETE FROM demo_sessions WHERE last_access < ?", cutoff.atOffset(ZoneOffset.UTC));
    }

    private DemoState load(UUID sessionId) {
        List<String> rows = jdbc.query(
            "SELECT state_json FROM demo_sessions WHERE id = ?",
            (resultSet, rowNumber) -> resultSet.getString(1),
            sessionId
        );
        if (rows.isEmpty()) {
            return new DemoState();
        }
        try {
            return objectMapper.readValue(rows.get(0), StateSnapshot.class).toState();
        } catch (JacksonException exception) {
            throw new IllegalStateException("저장된 데모 상태를 읽을 수 없습니다.", exception);
        }
    }

    private void persist(UUID sessionId, DemoState state) {
        state.lastAccess = Instant.now();
        StateSnapshot snapshot = state.snapshot();
        String json;
        try {
            json = objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            throw new IllegalStateException("데모 상태를 저장할 수 없습니다.", exception);
        }

        int updated = jdbc.update(
            "UPDATE demo_sessions SET state_json = ?, last_access = ? WHERE id = ?",
            json,
            state.lastAccess.atOffset(ZoneOffset.UTC),
            sessionId
        );
        if (updated == 0) {
            try {
                jdbc.update(
                    "INSERT INTO demo_sessions (id, state_json, last_access) VALUES (?, ?, ?)",
                    sessionId,
                    json,
                    state.lastAccess.atOffset(ZoneOffset.UTC)
                );
            } catch (DuplicateKeyException ignored) {
                jdbc.update(
                    "UPDATE demo_sessions SET state_json = ?, last_access = ? WHERE id = ?",
                    json,
                    state.lastAccess.atOffset(ZoneOffset.UTC),
                    sessionId
                );
            }
        }
    }

    private static void validateJob(Map<String, Object> job) {
        for (String field : List.of("workType", "location")) {
            if (!(job.get(field) instanceof String value) || value.isBlank()) {
                invalidJob(field);
            }
        }

        LocalDate startDate = parseDate(job.get("startDate"), "startDate");
        LocalDate endDate = parseDate(job.get("endDate"), "endDate");
        if (endDate.isBefore(startDate)) {
            invalidJob("endDate");
        }
        requirePositiveInteger(job.get("workersNeeded"), "workersNeeded");
        requirePositiveInteger(job.get("dailyPay"), "dailyPay");

        Object hazardFlags = job.get("hazardFlags");
        if (!(hazardFlags instanceof List<?> flags) || flags.stream().anyMatch(flag -> !(flag instanceof String))) {
            invalidJob("hazardFlags");
        }

    }

    private static void validateMissingFields(Object value, boolean required) {
        if (value == null) {
            if (required) {
                invalidJob("missingFields");
            }
            return;
        }
        if (!(value instanceof List<?> fields)
            || fields.stream().anyMatch(field -> !(field instanceof String) || ((String) field).isBlank())) {
            invalidJob("missingFields");
        }
        if (!((List<?>) value).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "필수 작업 조건을 먼저 확인해 주세요.");
        }
    }

    private static LocalDate parseDate(Object value, String field) {
        if (!(value instanceof String text)) {
            invalidJob(field);
        }
        try {
            return LocalDate.parse((String) value);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "필수 작업 조건을 확인할 수 없습니다: " + field,
                exception
            );
        }
    }

    private static void requirePositiveInteger(Object value, String field) {
        if (!(value instanceof Number number)) {
            invalidJob(field);
        }
        try {
            if (new BigDecimal(((Number) value).toString()).stripTrailingZeros().scale() > 0
                || new BigDecimal(((Number) value).toString()).signum() <= 0) {
                invalidJob(field);
            }
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "필수 작업 조건을 확인할 수 없습니다: " + field,
                exception
            );
        }
    }

    private static void invalidJob(String field) {
        throw new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "필수 작업 조건을 확인할 수 없습니다: " + field
        );
    }

    private static void invalidAnalysisResult() {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "추천 결과 형식이 올바르지 않습니다. 다시 분석해 주세요.");
    }

    private static boolean hasHazards(Map<String, Object> job) {
        Object flags = job.get("hazardFlags");
        return flags instanceof List<?> list && !list.isEmpty();
    }

    private static Map<String, Object> objectValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private static Match matchFrom(String jobId, Map<?, ?> candidate) {
        Object workerValue = candidate.get("workerId");
        if (!(workerValue instanceof String) || ((String) workerValue).isBlank()) {
            invalidAnalysisResult();
        }
        String workerId = (String) workerValue;
        String id = "match-" + UUID.randomUUID();
        Object scoreValue = candidate.containsKey("score") ? candidate.get("score") : candidate.get("totalScore");
        int score = parseScore(scoreValue);
        Object reasonValue = candidate.containsKey("scoreReasons") ? candidate.get("scoreReasons") : candidate.get("reasons");
        if (!(reasonValue instanceof List<?> values)
            || values.stream().anyMatch(value -> !(value instanceof String))) {
            invalidAnalysisResult();
        }
        List<String> reasons = ((List<?>) reasonValue).stream().map(String.class::cast).toList();
        Object nicknameValue = candidate.get("nickname");
        String nickname = nicknameValue instanceof String value && !value.isBlank() ? value : workerId;
        return new Match(id, jobId, workerId, nickname, score, List.copyOf(reasons), false, false, "CANDIDATES_READY");
    }

    private static int parseScore(Object value) {
        if (!(value instanceof Number)) {
            invalidAnalysisResult();
        }
        try {
            int score = new BigDecimal(((Number) value).toString()).intValueExact();
            if (score < 0 || score > 100) {
                invalidAnalysisResult();
            }
            return score;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "추천 점수 형식이 올바르지 않습니다.", exception);
        }
    }

    public record PendingAnalysis(String id, Map<String, Object> analysis) {}

    public record Match(
        String id,
        String jobId,
        String workerId,
        String nickname,
        int totalScore,
        List<String> scoreReasons,
        boolean farmerApproved,
        boolean workerAccepted,
        String status
    ) {
        Match withFarmerApproved(boolean approved) {
            return new Match(id, jobId, workerId, nickname, totalScore, scoreReasons, approved, workerAccepted,
                approved && workerAccepted ? "CONFIRMED" : "FARMER_APPROVED");
        }

        Match withWorkerAccepted(boolean accepted) {
            return new Match(id, jobId, workerId, nickname, totalScore, scoreReasons, farmerApproved, accepted,
                farmerApproved && accepted ? "CONFIRMED" : "FARMER_APPROVED");
        }
    }

    public static class DemoState {
        private String jobId;
        private Map<String, Object> job = Map.of();
        private PendingAnalysis pendingAnalysis;
        private boolean safetyConfirmed;
        private volatile Instant lastAccess = Instant.now();
        private final Map<String, Match> matches = new ConcurrentHashMap<>();

        private StateSnapshot snapshot() {
            return new StateSnapshot(jobId, jobId == null ? null : job, pendingAnalysis, safetyConfirmed, List.copyOf(matches.values()), lastAccess);
        }

        private static DemoState fromSnapshot(StateSnapshot snapshot) {
            DemoState state = new DemoState();
            state.jobId = snapshot.jobId();
            state.job = snapshot.job() == null ? Map.of() : snapshot.job();
            state.pendingAnalysis = snapshot.pendingAnalysis();
            state.safetyConfirmed = snapshot.safetyConfirmed();
            state.lastAccess = snapshot.lastAccess() == null ? Instant.now() : snapshot.lastAccess();
            if (snapshot.matches() != null) {
                snapshot.matches().forEach(match -> state.matches.put(match.id(), match));
            }
            return state;
        }

        public String getJobId() { return jobId; }
        public Map<String, Object> getJob() { return jobId == null ? null : job; }
        public boolean isSafetyConfirmed() { return safetyConfirmed; }
        public List<Match> getMatches() { return List.copyOf(matches.values()); }
    }

    public record StateSnapshot(
        String jobId,
        Map<String, Object> job,
        PendingAnalysis pendingAnalysis,
        boolean safetyConfirmed,
        List<Match> matches,
        Instant lastAccess
    ) {
        DemoState toState() {
            return DemoState.fromSnapshot(this);
        }
    }
}
