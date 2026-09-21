package com.dp.deviceops.parser.runtime.model;

import com.dp.deviceops.parser.semantic.ParserCoordinate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParserRuntimeModelTest {

    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");

    @Test
    void releaseRequiresMatchingCoordinateAndPassedValidationBeforePublication() {
        ParserCoordinate coordinate = coordinate();
        ParserReleaseValidation validation = new ParserReleaseValidation(
                "release-1", 1, true, 2, List.of(), NOW);

        ParserRelease published = new ParserRelease("release-1", "device-show-tech", "1.0.0",
                ReleaseState.PUBLISHED, coordinate, 1, validation, NOW, NOW);

        assertEquals("release-1", published.releaseId());
        assertThrows(IllegalArgumentException.class, () -> new ParserRelease(
                "release-1", "different-log", "1.0.0", ReleaseState.DRAFT,
                coordinate, 1, null, NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new ParserRelease(
                "release-1", "device-show-tech", "1.0.0", ReleaseState.PUBLISHED,
                coordinate, 1, null, NOW, NOW));
    }

    @Test
    void taskRequiresConsistentWaitingLeaseAndResultStates() {
        assertThrows(IllegalArgumentException.class, () -> task(
                ParseTaskState.WAITING, null, null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> task(
                ParseTaskState.RUNNING, null, null, NOW.plusSeconds(30), 1));

        ParseTask waiting = task(ParseTaskState.WAITING,
                ParseWaitReason.NO_CAPABLE_WORKER, null, null, 0);
        ParseTask succeeded = task(ParseTaskState.SUCCEEDED, null, null, null, 0);
        assertEquals(ParseWaitReason.NO_CAPABLE_WORKER, waiting.waitReason());
        assertEquals("result-1", succeeded.resultId());
    }

    @Test
    void collectionsAreDefensivelyCopiedAndContextIsOptional() {
        List<ParserReleaseValidation.CaseFailure> failures = new ArrayList<>();
        failures.add(new ParserReleaseValidation.CaseFailure("case-1", "MISMATCH", "different output"));
        ParserReleaseValidation validation = new ParserReleaseValidation(
                "release-1", 1, false, 1, failures, NOW);
        failures.clear();
        Map<String, Object> context = new HashMap<>();
        context.put("project", "P-001");
        ParseTask task = task(ParseTaskState.QUEUED, null, null, null, 0, context);
        context.clear();

        assertEquals(1, validation.failures().size());
        assertEquals("P-001", task.contextSnapshot().get("project"));
        assertEquals(Map.of(), task(ParseTaskState.QUEUED, null, null, null, 0, null).contextSnapshot());
        assertThrows(UnsupportedOperationException.class,
                () -> task.contextSnapshot().put("device", "D-001"));
    }

    @Test
    void extensionCoordinatesMustBeCompletePairs() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkerCapability("1.0.0", "vendor-extension", null));
        assertThrows(IllegalArgumentException.class,
                () -> new ParserCoordinate("device-show-tech", "1.0.0", "1.0.0",
                        "1.0.0", "1.0.0", "vendor-extension", null));
    }

    private static ParseTask task(ParseTaskState state, ParseWaitReason waitReason,
            String leaseOwner, Instant leaseExpiresAt, long leaseGeneration) {
        return task(state, waitReason, leaseOwner, leaseExpiresAt, leaseGeneration, Map.of());
    }

    private static ParseTask task(ParseTaskState state, ParseWaitReason waitReason,
            String leaseOwner, Instant leaseExpiresAt, long leaseGeneration, Map<String, Object> context) {
        return new ParseTask("task-1", "request-1", "standalone", "device-show-tech",
                "release-1", coordinate(), "command-output-block/v1", "payload-1", context,
                null, null, null, state, waitReason, 0, null, leaseOwner, leaseGeneration,
                leaseExpiresAt, state == ParseTaskState.SUCCEEDED ? "result-1" : null, NOW, NOW);
    }

    private static ParserCoordinate coordinate() {
        return new ParserCoordinate("device-show-tech", "1.0.0", "1.0.0",
                "1.0.0", "1.0.0", null, null);
    }
}
