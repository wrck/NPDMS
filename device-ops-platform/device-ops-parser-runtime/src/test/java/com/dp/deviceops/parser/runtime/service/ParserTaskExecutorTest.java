package com.dp.deviceops.parser.runtime.service;

import com.dp.deviceops.parser.runtime.model.WorkerCapability;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository;
import com.dp.deviceops.parser.runtime.port.ParserPayloadStore;
import com.dp.deviceops.parser.semantic.DefaultDynamicSemanticParser;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.input.ParserInputSource;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ParserTaskExecutorTest {

    private static final String VALID_INPUT = """
            {"schemaVersion":"1.0.0","commandBlocks":[{"commandIndex":1,
            "commandText":"event","status":"SUCCEEDED","stdout":"event","stderr":"",
            "receivedBytes":5,"pageCount":1,"truncated":false,"exitCode":0}]}
            """;

    @Test
    void cachedExactPlanCompletesWhenRegistryIsUnavailable() {
        RuntimeServiceTestFixture releases = new RuntimeServiceTestFixture();
        var bundle = RuntimeServiceTestFixture.bundle("runtime-log", "1.0.0");
        ParserPlanCompiler compiler = new ParserPlanCompiler();
        ParserPlanCache cache = new ParserPlanCache(2);
        cache.getOrLoad("release-1", () -> compiler.compile(bundle));
        RecordingTasks tasks = new RecordingTasks();

        executor(releases, payloads(VALID_INPUT), tasks, compiler, cache).execute(task("release-1", 1));

        assertNotNull(tasks.completion);
        assertNull(tasks.waiting);
        assertEquals("task-1", tasks.completion.taskId());
    }

    @Test
    void unavailableArtifactWaitsAndInvalidInputFails() {
        RuntimeServiceTestFixture unavailable = new RuntimeServiceTestFixture();
        RecordingTasks waiting = new RecordingTasks();
        executor(unavailable, payloads(VALID_INPUT), waiting, new ParserPlanCompiler(), new ParserPlanCache(2))
                .execute(task("missing", 1));
        assertEquals("PARSER_ARTIFACT_UNAVAILABLE", waiting.waiting.errorCode());

        RuntimeServiceTestFixture releases = new RuntimeServiceTestFixture();
        releases.bundles.put("release-1", RuntimeServiceTestFixture.bundle("runtime-log", "1.0.0"));
        RecordingTasks failed = new RecordingTasks();
        executor(releases, payloads("not-json"), failed, new ParserPlanCompiler(), new ParserPlanCache(2))
                .execute(task("release-1", 1));
        assertEquals("INVALID_INPUT", failed.failure.errorCode());

        RecordingTasks unreadable = new RecordingTasks();
        ParserPayloadStore unavailablePayload = new ParserPayloadStore() {
            @Override public String put(String mediaType, InputStream content) { return "unused"; }
            @Override public String putScoped(String namespace, String mediaType, InputStream content) {
                throw new UnsupportedOperationException();
            }
            @Override public boolean isOwnedBy(String namespace, String inputRef) { return false; }
            @Override public ParserInputSource open(String inputRef) {
                return () -> { throw new IOException("temporary read failure"); };
            }
        };
        executor(releases, unavailablePayload, unreadable, new ParserPlanCompiler(), new ParserPlanCache(2))
                .execute(task("release-1", 1));
        assertEquals("PARSER_PAYLOAD_UNAVAILABLE", unreadable.waiting.errorCode());
    }

    @Test
    void staleGenerationCannotWriteFallbackState() {
        RuntimeServiceTestFixture releases = new RuntimeServiceTestFixture();
        releases.bundles.put("release-1", RuntimeServiceTestFixture.bundle("runtime-log", "1.0.0"));
        RecordingTasks tasks = new RecordingTasks();
        tasks.loseLeaseOnComplete = true;

        executor(releases, payloads(VALID_INPUT), tasks, new ParserPlanCompiler(), new ParserPlanCache(2))
                .execute(task("release-1", 9));

        assertNull(tasks.completion);
        assertNull(tasks.waiting);
        assertNull(tasks.failure);
    }

    private static ParserTaskExecutor executor(RuntimeServiceTestFixture releases, ParserPayloadStore payloads,
            RecordingTasks tasks, ParserPlanCompiler compiler, ParserPlanCache cache) {
        return new ParserTaskExecutor("worker-1", releases, payloads, tasks, compiler,
                new DefaultDynamicSemanticParser(), cache,
                Clock.fixed(RuntimeServiceTestFixture.NOW, ZoneOffset.UTC), Duration.ofSeconds(5));
    }

    private static ParseTaskRepository.ClaimedTask task(String releaseId, long generation) {
        return new ParseTaskRepository.ClaimedTask("task-1", releaseId,
                new ParserCoordinate("runtime-log", "1.0.0", "1.0.0", "1.0.0", "1.0.0", null, null),
                "payload-1", generation);
    }

    private static ParserPayloadStore payloads(String input) {
        return new ParserPayloadStore() {
            @Override public String put(String mediaType, InputStream content) throws IOException { return "unused"; }
            @Override public String putScoped(String namespace, String mediaType, InputStream content) {
                throw new UnsupportedOperationException();
            }
            @Override public boolean isOwnedBy(String namespace, String inputRef) { return false; }
            @Override public ParserInputSource open(String inputRef) {
                return () -> new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    private static final class RecordingTasks implements ParseTaskRepository {
        private Completion completion;
        private Waiting waiting;
        private Failure failure;
        private boolean loseLeaseOnComplete;

        @Override public void complete(Completion value) {
            if (loseLeaseOnComplete) throw new ParserRuntimeError("PARSER_LEASE_LOST");
            completion = value;
        }
        @Override public void waitFor(Waiting value) { waiting = value; }
        @Override public void fail(Failure value) { failure = value; }
        @Override public java.util.Optional<SubmitOutcome> findSubmission(SubmissionIdentity request) {
            throw new UnsupportedOperationException();
        }
        @Override public SubmitOutcome submit(SubmitRequest request) { throw new UnsupportedOperationException(); }
        @Override public List<ClaimedTask> claim(String workerId, Set<WorkerCapability> capabilities,
                java.time.Instant now, java.time.Instant leaseUntil, int limit) { return List.of(); }
        @Override public void renew(String taskId, String workerId, long leaseGeneration,
                java.time.Instant leaseUntil) { }
        @Override public void releaseClaim(String taskId, String workerId, long leaseGeneration,
                java.time.Instant nextAttemptAt) { }
        @Override public void cancel(String taskId, String callerNamespace) { }
        @Override public SubmitOutcome retry(String taskId, String callerNamespace,
                java.time.Instant nextAttemptAt) { throw new UnsupportedOperationException(); }
        @Override public void terminateWaiting(String taskId, String callerNamespace, String errorCode,
                java.time.Instant at) { }
        @Override public int recoverExpired(java.time.Instant now, int limit) { return 0; }
    }
}
