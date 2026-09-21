package com.dp.deviceops.parser.runtime.service;

import com.dp.deviceops.parser.runtime.model.ParseTask;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository.SubmitOutcome;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.dp.deviceops.parser.runtime.service.RuntimeServiceTestFixture.INPUT_FORMAT;
import static com.dp.deviceops.parser.runtime.service.RuntimeServiceTestFixture.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParseTaskServiceTest {

    @Test
    void pinsActiveReleaseAndRejectsConflictingIdempotentReuse() {
        RuntimeServiceTestFixture repository = new RuntimeServiceTestFixture();
        repository.addPublished("release-1", "log-a", "1.0.0");
        repository.active.put("log-a", "release-1");
        ParseTaskService service = service(repository);

        SubmitOutcome first = service.submit(command("request-1", null, "payload-1", null));
        SubmitOutcome repeated = service.submit(command("request-1", null, "payload-1", null));

        assertEquals("release-1", first.releaseId());
        assertEquals(first.taskId(), repeated.taskId());
        ParserRuntimeError conflict = assertThrows(ParserRuntimeError.class,
                () -> service.submit(command("request-1", null, "payload-2", null)));
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
    }

    @Test
    void unchangedRetryReplaysPinnedReleaseAfterActiveSwitchOrRemoval() {
        RuntimeServiceTestFixture repository = new RuntimeServiceTestFixture();
        repository.addPublished("release-1", "log-a", "1.0.0");
        repository.addPublished("release-2", "log-a", "2.0.0");
        repository.active.put("log-a", "release-1");
        ParseTaskService service = service(repository);
        var command = command("request-1", null, "payload-1", null);
        SubmitOutcome first = service.submit(command);

        repository.active.put("log-a", "release-2");
        assertEquals(first, service.submit(command));
        repository.active.clear();
        repository.releases.clear();
        repository.bundles.clear();
        assertEquals(first, service.submit(command));
        assertEquals(1, repository.tasks.size());
    }

    @Test
    void unchangedExplicitRetryDoesNotResolveDisabledReleaseOrRemovedConsumer() {
        RuntimeServiceTestFixture repository = new RuntimeServiceTestFixture();
        repository.addPublished("release-1", "log-a", "1.0.0");
        var command = command("request-1", "release-1", "payload-1", "npdp");
        SubmitOutcome first = service(repository).submit(command);
        repository.disable("release-1");
        ParseTaskService withoutConsumers = new ParseTaskService(repository, repository, repository,
                ignored -> { throw new AssertionError("replay must not resolve consumer"); },
                Clock.fixed(NOW, ZoneOffset.UTC), () -> { throw new AssertionError("replay must not allocate id"); });

        assertEquals(first, withoutConsumers.submit(command));
    }

    @Test
    void changedSemanticsConflictBeforeReleaseOrConsumerResolution() {
        RuntimeServiceTestFixture repository = new RuntimeServiceTestFixture();
        repository.addPublished("release-1", "log-a", "1.0.0");
        repository.active.put("log-a", "release-1");
        ParseTaskService service = service(repository);
        service.submit(command("request-1", null, "payload-1", null));

        var changed = java.util.List.of(
                new ParseTaskService.SubmitCommand("request-1", "npdp", "other-log", null,
                        INPUT_FORMAT, "payload-1", Map.of("projectKey", "P-001"), null, null),
                new ParseTaskService.SubmitCommand("request-1", "npdp", "log-a", null,
                        "other-format", "payload-1", Map.of("projectKey", "P-001"), null, null),
                new ParseTaskService.SubmitCommand("request-1", "npdp", "log-a", null,
                        INPUT_FORMAT, "payload-1", Map.of("projectKey", "P-002"), null, null),
                new ParseTaskService.SubmitCommand("request-1", "npdp", "log-a", null,
                        INPUT_FORMAT, "payload-1", Map.of("projectKey", "P-001"), "source-2", null),
                command("request-1", null, "payload-1", "missing-consumer"),
                command("request-1", "release-1", "payload-1", null),
                command("request-1", "missing-release", "payload-1", null));
        for (var retry : changed) {
            assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(ParserRuntimeError.class,
                    () -> service.submit(retry)).code(), retry.toString());
        }
    }

    @Test
    void sameContextWithDifferentMapOrderReplaysButNamespacesAreIndependent() {
        RuntimeServiceTestFixture repository = new RuntimeServiceTestFixture();
        repository.addPublished("release-1", "log-a", "1.0.0");
        repository.active.put("log-a", "release-1");
        ParseTaskService service = service(repository);
        Map<String, Object> context = new java.util.LinkedHashMap<>();
        context.put("z", Map.of("device", "one"));
        context.put("a", java.util.List.of(1, 2));
        var first = service.submit(new ParseTaskService.SubmitCommand("request-1", "npdp", "log-a", null,
                INPUT_FORMAT, "payload-1", context, null, null));
        assertEquals(first, service.submit(new ParseTaskService.SubmitCommand("request-1", "npdp", "log-a", null,
                INPUT_FORMAT, "payload-1", Map.of("a", java.util.List.of(1, 2), "z", Map.of("device", "one")), null, null)));
        var other = service.submit(new ParseTaskService.SubmitCommand("request-1", "other", "log-a", null,
                INPUT_FORMAT, "payload-1", context, null, null));
        org.junit.jupiter.api.Assertions.assertNotEquals(first.taskId(), other.taskId());
    }

    @Test
    void retryKeepsPinnedReleaseAndReparseLinksTheSourceResult() {
        RuntimeServiceTestFixture repository = new RuntimeServiceTestFixture();
        repository.addPublished("release-old", "log-a", "1.0.0");
        repository.addPublished("release-new", "log-a", "2.0.0");
        repository.active.put("log-a", "release-old");
        ParseTaskService service = service(repository);
        SubmitOutcome original = service.submit(command("request-old", null, "payload-1", null));
        ParseTask originalTask = repository.tasks.get(original.taskId());
        repository.addResult("result-old", originalTask);

        assertEquals("release-old", service.retry("npdp", original.taskId()).releaseId());
        SubmitOutcome reparsed = service.reparse("npdp", "result-old", "release-new", "reparse-002");

        assertEquals("release-new", reparsed.releaseId());
        assertEquals("result-old", reparsed.sourceResultId());
    }

    @Test
    void resolvesOnlyPreconfiguredConsumersAndRejectsDisabledReleases() {
        RuntimeServiceTestFixture repository = new RuntimeServiceTestFixture();
        repository.addPublished("release-1", "log-a", "1.0.0");
        repository.active.put("log-a", "release-1");
        ParseTaskService service = service(repository);

        SubmitOutcome submitted = service.submit(command("request-1", null, "payload-1", "npdp"));
        assertEquals(URI.create("https://npdp.example/results"),
                repository.tasks.get(submitted.taskId()).resultDestination());
        ParserRuntimeError unknown = assertThrows(ParserRuntimeError.class,
                () -> service.submit(command("request-2", null, "payload-1", "unknown")));
        assertEquals("RESULT_CONSUMER_NOT_FOUND", unknown.code());

        repository.disable("release-1");
        ParserRuntimeError disabled = assertThrows(ParserRuntimeError.class,
                () -> service.submit(command("request-3", "release-1", "payload-1", null)));
        assertEquals("RELEASE_NOT_PUBLISHED", disabled.code());
    }

    private static ParseTaskService service(RuntimeServiceTestFixture repository) {
        AtomicInteger sequence = new AtomicInteger();
        ResultConsumerRegistry consumers = consumerId -> "npdp".equals(consumerId)
                ? Optional.of(URI.create("https://npdp.example/results")) : Optional.empty();
        return new ParseTaskService(repository, repository, repository, consumers,
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "task-" + sequence.incrementAndGet());
    }

    private static ParseTaskService.SubmitCommand command(
            String requestId, String releaseId, String inputRef, String consumerId) {
        return new ParseTaskService.SubmitCommand(requestId, "npdp", "log-a", releaseId,
                INPUT_FORMAT, inputRef, Map.of("projectKey", "P-001"), null, consumerId);
    }
}
