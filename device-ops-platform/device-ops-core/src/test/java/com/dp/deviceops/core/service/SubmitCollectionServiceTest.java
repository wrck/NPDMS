package com.dp.deviceops.core.service;

import com.dp.deviceops.core.model.CollectionContextSnapshot;
import com.dp.deviceops.core.model.CollectionTarget;
import com.dp.deviceops.core.model.CollectionTask;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.port.CollectionRepository;
import com.dp.deviceops.core.port.OutputParser;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SubmitCollectionServiceTest {

    @Test
    void exposesThePublicSubmissionServiceContract() {
        try {
            Class<?> contract = Class.forName("com.dp.deviceops.core.service.SubmitCollectionService");
            assertTrue(java.lang.reflect.Modifier.isPublic(contract.getModifiers()));
        } catch (ClassNotFoundException exception) {
            fail("SubmitCollectionService public contract is required", exception);
        }
    }

    @Test
    void exposesNullableFingerprintWithSourceCompatibleSubmissionOverloads() {
        assertTrue(java.util.Arrays.stream(CollectionTask.class.getMethods())
                .anyMatch(method -> method.getName().equals("submissionFingerprint")
                        && method.getReturnType() == String.class), "CollectionTask fingerprint getter is required");
        assertTrue(java.util.Arrays.stream(SubmitCollectionService.SubmitCollectionCommand.class.getRecordComponents())
                .anyMatch(component -> component.getName().equals("submissionFingerprint")
                        && component.getType() == String.class), "command fingerprint component is required");
    }

    @Test
    void persistsFingerprintAndReplaysIdenticalSubmissionWithoutSavingAgain() {
        InMemoryRepository repository = new InMemoryRepository();
        SubmitCollectionService service = new SubmitCollectionService(repository, () -> "fingerprinted");
        var command = fingerprintedCommand("project-42", "v1:original");
        assertFalse(service.submit(command).existing());
        assertEquals("v1:original", repository.lastSaved.submissionFingerprint());
        assertTrue(service.submit(command).existing());
        assertEquals(1, repository.saveCalls);
    }

    @Test
    void changedFingerprintOrProjectConflictsBeforeSavingOrAllocatingAnId() {
        InMemoryRepository repository = new InMemoryRepository();
        SubmitCollectionService service = new SubmitCollectionService(repository, new SequenceIdSupplier("original"));
        service.submit(fingerprintedCommand("project-42", "v1:original"));
        assertConflict(() -> service.submit(fingerprintedCommand("project-42", "v1:changed")));
        assertConflict(() -> service.submit(fingerprintedCommand("project-99", "v1:original")));
        assertEquals(1, repository.saveCalls);
    }

    @Test
    void legacyMissingFingerprintIsSafelyRejectedButOldCallersKeepReplayBehavior() {
        InMemoryRepository repository = new InMemoryRepository();
        SubmitCollectionService service = new SubmitCollectionService(repository, new SequenceIdSupplier("legacy"));
        service.submit(fingerprintedCommand("project-42", null));
        assertConflict(() -> service.submit(fingerprintedCommand("project-42", "v1:new")));
        assertTrue(service.submit(fingerprintedCommand("project-99", null)).existing());
        assertEquals(1, repository.saveCalls);
    }

    @Test
    void prelookupEnforcesFingerprintWithoutRevealingExistingId() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        SubmitCollectionService service = new SubmitCollectionService(repository, () -> "original");
        service.submit(fingerprintedCommand("project-42", "v1:original"));
        var lookup = java.util.Arrays.stream(SubmitCollectionService.class.getMethods())
                .filter(method -> method.getName().equals("findExistingId") && method.getParameterCount() == 3)
                .findFirst();
        assertTrue(lookup.isPresent(), "fingerprint-aware findExistingId overload is required");
        assertEquals(Optional.of("original"), lookup.orElseThrow().invoke(service, "pms", "idem-1", "v1:original"));
        var mismatch = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> lookup.orElseThrow().invoke(service, "pms", "idem-1", "v1:changed"));
        assertEquals("CollectionIdempotencyConflictException", mismatch.getCause().getClass().getSimpleName());
        assertEquals(Optional.of("original"), service.findExistingId("pms", "idem-1"));
    }

    @Test
    void concurrentWinnerMustMatchFingerprintEvenWhenRepositoryDoesNotValidateIt() {
        var winnerCommand = fingerprintedCommand("project-42", "v1:winner");
        CollectionTask winner = CollectionTask.submitted("winner", "pms", "project-42", "request-1", "idem-1",
                winnerCommand.targets(), winnerCommand.script(), null, null, null, "v1:winner");
        CollectionRepository repository = new CollectionRepository() {
            public Optional<CollectionTask> findByIdempotencyKey(String namespace, String key) { return Optional.empty(); }
            public SaveResult saveOrGetExisting(CollectionTask candidate) { return new SaveResult(winner, false); }
        };
        SubmitCollectionService service = new SubmitCollectionService(repository, () -> "loser");
        assertConflict(() -> service.submit(fingerprintedCommand("project-42", "v1:loser")));
        assertEquals("winner", service.submit(winnerCommand).collectionId());
    }

    @Test
    void wrongCaseNamespaceCannotRevealAnExistingTaskFromALooseRepository() {
        CollectionTask winner = submittedTask("private-task-id", List.of(target("pms", "project-42")));
        CollectionRepository repository = new CollectionRepository() {
            public Optional<CollectionTask> findByIdempotencyKey(String namespace, String key) { return Optional.of(winner); }
            public SaveResult saveOrGetExisting(CollectionTask candidate) { throw new AssertionError("must not save"); }
        };
        SubmitCollectionService service = new SubmitCollectionService(repository, () -> "unused");
        assertTrue(service.findExistingId("PMS", "idem-1").isEmpty());
    }

    private static SubmitCollectionService.SubmitCollectionCommand fingerprintedCommand(String project, String fingerprint) {
        return new SubmitCollectionService.SubmitCollectionCommand("pms", project, "request-1", "idem-1",
                List.of(target("pms", project)), script(), null, null, null, fingerprint);
    }

    private static void assertConflict(org.junit.jupiter.api.function.Executable submission) {
        RuntimeException conflict = assertThrows(RuntimeException.class, submission);
        assertEquals("CollectionIdempotencyConflictException", conflict.getClass().getSimpleName());
        assertFalse(conflict.getMessage().contains("original"));
        assertFalse(conflict.getMessage().contains("private-task-id"));
    }

    @Test
    void repeatsAnIdempotencyKeyByReturningTheExistingTaskWithoutAnotherSave() {
        InMemoryRepository repository = new InMemoryRepository();
        SubmitCollectionService service = new SubmitCollectionService(repository, () -> "collection-2");
        SubmitCollectionService.SubmitCollectionCommand command = command("pms", "project-42", List.of(target("pms", "project-42")));

        SubmitCollectionService.SubmitCollectionResult first = service.submit(command);
        SubmitCollectionService.SubmitCollectionResult repeated = service.submit(command);

        assertEquals("collection-2", first.collectionId());
        assertFalse(first.existing());
        assertEquals("collection-2", repeated.collectionId());
        assertTrue(repeated.existing());
        assertEquals(1, repository.saveCalls);
    }

    @Test
    void treatsTheSameIdempotencyKeyInAnotherNamespaceAsIndependent() {
        InMemoryRepository repository = new InMemoryRepository();
        SubmitCollectionService service = new SubmitCollectionService(repository,
                new SequenceIdSupplier("collection-1", "collection-2"));

        SubmitCollectionService.SubmitCollectionResult first = service.submit(command("pms", "project-42", List.of(target("pms", "project-42"))));
        SubmitCollectionService.SubmitCollectionResult second = service.submit(command("external", "project-42", List.of(target("external", "project-42"))));

        assertEquals("collection-1", first.collectionId());
        assertEquals("collection-2", second.collectionId());
        assertFalse(second.existing());
        assertEquals(2, repository.saveCalls);
    }

    @Test
    void reportsAnAtomicConcurrentWinnerAsExistingAfterTheFastLookupMisses() {
        CollectionTask winningTask = submittedTask("collection-winner", List.of(target("pms", "project-42")));
        CollectionRepository repository = new CollectionRepository() {
            @Override
            public Optional<CollectionTask> findByIdempotencyKey(String namespace, String key) {
                return Optional.empty();
            }

            @Override
            public CollectionRepository.SaveResult saveOrGetExisting(CollectionTask candidate) {
                return new CollectionRepository.SaveResult(winningTask, false);
            }
        };
        SubmitCollectionService service = new SubmitCollectionService(repository, () -> "collection-loser");

        SubmitCollectionService.SubmitCollectionResult result = service.submit(
                command("pms", "project-42", List.of(target("pms", "project-42"))));

        assertEquals("collection-winner", result.collectionId());
        assertTrue(result.existing());
    }

    @Test
    void reportsAnAtomicNewWriteAsCreatedAfterTheFastLookupMisses() {
        CollectionRepository repository = new CollectionRepository() {
            @Override
            public Optional<CollectionTask> findByIdempotencyKey(String namespace, String key) {
                return Optional.empty();
            }

            @Override
            public CollectionRepository.SaveResult saveOrGetExisting(CollectionTask candidate) {
                return new CollectionRepository.SaveResult(candidate, true);
            }
        };
        SubmitCollectionService service = new SubmitCollectionService(repository, () -> "collection-created");

        SubmitCollectionService.SubmitCollectionResult result = service.submit(
                command("pms", "project-42", List.of(target("pms", "project-42"))));

        assertEquals("collection-created", result.collectionId());
        assertFalse(result.existing());
    }

    @Test
    void rejectsInvalidSubmissionInputsBeforeSaving() {
        InMemoryRepository repository = new InMemoryRepository();
        SubmitCollectionService service = new SubmitCollectionService(repository, () -> "collection-1");

        assertThrows(IllegalArgumentException.class, () -> service.submit(command(" ", "project-42", List.of(target("pms", "project-42")))));
        assertThrows(IllegalArgumentException.class, () -> service.submit(command("pms", "project-42", " ", List.of(target("pms", "project-42")), script())));
        assertThrows(IllegalArgumentException.class, () -> service.submit(command("pms", "project-42", List.of())));
        assertThrows(NullPointerException.class, () -> service.submit(command("pms", "project-42", "request-1", List.of(target("pms", "project-42")), null)));
        assertEquals(0, repository.saveCalls);
    }

    @Test
    void submitsTargetsWithoutProjectOrDeviceContextWhenNoCallbackIsRequested() {
        InMemoryRepository repository = new InMemoryRepository();
        SubmitCollectionService service = new SubmitCollectionService(repository, () -> "collection-unscoped");
        CollectionTarget target = unscopedTarget();
        SubmitCollectionService.SubmitCollectionCommand command = new SubmitCollectionService.SubmitCollectionCommand(
                "standalone", null, "request-1", "idem-unscoped", List.of(target),
                script(), null, null);

        SubmitCollectionService.SubmitCollectionResult result = service.submit(command);

        assertEquals("collection-unscoped", result.collectionId());
        assertTrue(repository.lastSaved.projectKey().isEmpty());
        assertTrue(target.contextSnapshot().project().isEmpty());
        assertTrue(target.contextSnapshot().device().isEmpty());
    }

    @Test
    void rejectsCallbackWhenAnyTargetLacksProjectOrDeviceContext() {
        InMemoryRepository repository = new InMemoryRepository();
        SubmitCollectionService service = new SubmitCollectionService(repository, () -> "collection-1");
        SubmitCollectionService.SubmitCollectionCommand command = new SubmitCollectionService.SubmitCollectionCommand(
                "pms", null, "request-1", "idem-1", List.of(unscopedTarget()),
                script(), null, URI.create("https://callback.example.test/collections/request-1"));

        assertThrows(IllegalArgumentException.class, () -> service.submit(command));
        assertEquals(0, repository.saveCalls);
    }

    @Test
    void rejectsTargetsWhoseSnapshotDoesNotBelongToTheSubmittedProjectBeforeSaving() {
        InMemoryRepository repository = new InMemoryRepository();
        SubmitCollectionService service = new SubmitCollectionService(repository, () -> "collection-1");

        assertThrows(IllegalArgumentException.class, () -> service.submit(command("pms", "project-42", List.of(target("external", "project-42")))));
        assertThrows(IllegalArgumentException.class, () -> service.submit(command("pms", "project-42", List.of(target("pms", "project-99")))));
        assertEquals(0, repository.saveCalls);
    }

    @Test
    void savesAllSubmissionMetadataAndSnapshotsNeededForLaterExecution() {
        InMemoryRepository repository = new InMemoryRepository();
        SubmitCollectionService service = new SubmitCollectionService(repository, () -> "collection-1");
        ScriptArtifact script = script();
        URI callbackUri = URI.create("https://callback.example.test/collections/request-1");
        List<CollectionTarget> targets = List.of(target("pms", "project-42"));
        SubmitCollectionService.SubmitCollectionCommand command = new SubmitCollectionService.SubmitCollectionCommand(
                "pms", "project-42", "request-1", "idem-1", targets,
                script, "DELIVERY_CHECK", callbackUri);

        service.submit(command);

        CollectionTask saved = repository.lastSaved;
        assertEquals("collection-1", saved.taskId());
        assertEquals("pms", saved.namespace());
        assertEquals("project-42", saved.projectKey().orElseThrow());
        assertEquals("request-1", saved.externalRequestId());
        assertEquals("idem-1", saved.idempotencyKey());
        assertEquals("DELIVERY_CHECK", saved.activityType());
        assertEquals(callbackUri, saved.callbackUri());
        assertSame(script, saved.script());
        assertEquals(targets, saved.targets());
        assertThrows(UnsupportedOperationException.class, () -> saved.targets().add(target("pms", "project-42")));
    }

    @Test
    void defensivelyCopiesCommandTargetsBeforeSaving() {
        InMemoryRepository repository = new InMemoryRepository();
        SubmitCollectionService service = new SubmitCollectionService(repository, () -> "collection-1");
        List<CollectionTarget> sourceTargets = new ArrayList<>(List.of(target("pms", "project-42")));
        SubmitCollectionService.SubmitCollectionCommand command = command("pms", "project-42", sourceTargets);
        sourceTargets.add(target("pms", "project-42"));

        service.submit(command);

        assertEquals(1, command.targets().size());
        assertEquals(1, repository.lastSaved.targets().size());
    }

    @Test
    void parserRegistryRejectsBlankAndDuplicateTypesAndResolvesKnownParsers() {
        OutputParser parser = new OutputParser() {
            @Override
            public String type() {
                return "key-value";
            }

            @Override
            public ParseResult parse(String rawOutput, String configuration) {
                return new ParseResult(Map.of("hostname", rawOutput), List.of(configuration));
            }
        };

        assertThrows(IllegalArgumentException.class, () -> new OutputParserRegistry(List.of(blankParser())));
        assertThrows(IllegalArgumentException.class, () -> new OutputParserRegistry(List.of(parser, parserWithType("KEY-VALUE"))));

        OutputParserRegistry registry = new OutputParserRegistry(List.of(parser));
        assertSame(parser, registry.find("KEY-VALUE").orElseThrow());
    }

    @Test
    void parserResultsDefensivelyCopyReturnedCollections() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("hostname", "core-01");
        List<String> warnings = new ArrayList<>(List.of("partial"));

        OutputParser.ParseResult result = new OutputParser.ParseResult(values, warnings);
        values.put("hostname", "edge-01");
        warnings.add("truncated");

        assertEquals(Map.of("hostname", "core-01"), result.values());
        assertEquals(List.of("partial"), result.warnings());
        assertThrows(UnsupportedOperationException.class, () -> result.values().put("model", "R9000"));
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().add("another"));
    }

    private static SubmitCollectionService.SubmitCollectionCommand command(String namespace, String projectKey,
                                                                             List<CollectionTarget> targets) {
        return command(namespace, projectKey, "request-1", targets, script());
    }

    private static SubmitCollectionService.SubmitCollectionCommand command(String namespace, String projectKey,
                                                                             String idempotencyKey, List<CollectionTarget> targets,
                                                                             ScriptArtifact script) {
        return new SubmitCollectionService.SubmitCollectionCommand(namespace, projectKey, "request-1", idempotencyKey,
                targets, script, "DELIVERY_CHECK", URI.create("https://callback.example.test/collections/request-1"));
    }

    private static CollectionTarget target(String namespace, String projectKey) {
        CollectionContextSnapshot snapshot = CollectionContextSnapshot.of(namespace, projectKey, "Delivery", "P42",
                "device-9", "core-01", "Acme", "R9000", Map.of("region", "east"));
        return CollectionTarget.forSnapshot(snapshot, ConnectionProtocol.SSH2,
                "10.0.0.8", 22, "collector", "SHA256:abc", null);
    }

    private static CollectionTarget unscopedTarget() {
        return CollectionTarget.forSnapshot(CollectionContextSnapshot.ofOptional(null, null, Map.of()),
                ConnectionProtocol.SSH2, "10.0.0.8", 22, "collector", "SHA256:abc", null);
    }

    private static ScriptArtifact script() {
        return ScriptArtifact.external("cutover-check", "3", "display version", sha256("display version"),
                ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "KEY_VALUE", "strict=true");
    }

    private static CollectionTask submittedTask(String taskId, List<CollectionTarget> targets) {
        return CollectionTask.submitted(taskId, "pms", "project-42", "request-1", "idem-1", targets, script(),
                "DELIVERY_CHECK", URI.create("https://callback.example.test/collections/request-1"));
    }

    private static OutputParser blankParser() {
        return parserWithType(" ");
    }

    private static OutputParser parserWithType(String type) {
        return new OutputParser() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public ParseResult parse(String rawOutput, String configuration) {
                return new ParseResult(Map.of(), List.of());
            }
        };
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class InMemoryRepository implements CollectionRepository {

        private final Map<String, CollectionTask> tasksByIdempotency = new LinkedHashMap<>();
        private int saveCalls;
        private CollectionTask lastSaved;

        @Override
        public Optional<CollectionTask> findByIdempotencyKey(String namespace, String key) {
            return Optional.ofNullable(tasksByIdempotency.get(namespace + "/" + key));
        }

        @Override
        public CollectionRepository.SaveResult saveOrGetExisting(CollectionTask task) {
            saveCalls++;
            lastSaved = task;
            tasksByIdempotency.put(task.namespace() + "/" + task.idempotencyKey(), task);
            return new CollectionRepository.SaveResult(task, true);
        }
    }

    private static final class SequenceIdSupplier implements java.util.function.Supplier<String> {

        private final List<String> values;
        private int current;

        private SequenceIdSupplier(String... values) {
            this.values = List.of(values);
        }

        @Override
        public String get() {
            return values.get(current++);
        }
    }
}
