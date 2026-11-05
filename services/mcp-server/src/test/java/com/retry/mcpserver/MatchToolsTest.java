package com.retry.mcpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatchToolsTest {
    private final MatchTools tools = new MatchTools();

    @Test
    void calculatesOnlyDocumentedWeightsAndCapsAtOneHundred() {
        Map<String, Object> score = tools.calculateMatchScore(
            "worker-01", "복숭아 적과", "2026-11-09", "2026-11-11", 120000
        );

        assertEquals(94, score.get("totalScore"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> weights = (Map<String, Integer>) score.get("weights");
        assertEquals(30, weights.get("date"));
        assertEquals(25, weights.get("workType"));
        assertEquals(20, weights.get("distance"));
        assertEquals(9, weights.get("experience"));
        assertEquals(10, weights.get("pay"));
    }

    @Test
    void returnsCandidatesForCheongdoAndFiltersUnsupportedLocation() {
        List<Map<String, Object>> candidates = tools.findCandidateWorkers(
            "청도군 매전면", "복숭아 적과", "2026-11-09", "2026-11-11", 2, 120000
        );
        List<Map<String, Object>> outsideRegion = tools.findCandidateWorkers(
            "대구", "복숭아 적과", "2026-11-09", "2026-11-11", 2, 120000
        );

        assertFalse(candidates.isEmpty());
        assertTrue(outsideRegion.isEmpty());
    }

    @Test
    void safetyCheckRequiresConfirmationForHazardousWork() {
        Map<String, Object> result = tools.checkJobSafety("복숭아 적과", List.of("농기계 안전 점검"));

        assertEquals("REVIEW_REQUIRED", result.get("riskLevel"));
        assertEquals(true, result.get("confirmationRequired"));
    }
}
