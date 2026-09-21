package com.dp.deviceops.core.model;

import com.dp.deviceops.core.port.CommandExecutionPort;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionTaskTest {

    @Test
    void rejectsChangingARegisteredScriptVersionToDifferentContent() {
        ScriptArtifact original = ScriptArtifact.external(
                "cutover-check", "3", "display version", sha256("display version"),
                ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "NONE", null);

        assertThrows(ScriptArtifact.DomainConflictException.class, () -> original.assertSameVersion(
                ScriptArtifact.external("cutover-check", "3", "display clock",
                        sha256("display clock"), ScriptArtifact.PersistencePolicy.REGISTER_VERSION,
                        "NONE", null)));
    }

    @Test
    void rejectsBlankScriptKeysAndInconsistentSha256Values() {
        assertThrows(IllegalArgumentException.class, () -> ScriptArtifact.external(
                " ", "3", "display version", sha256("display version"),
                ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "NONE", null));
        assertThrows(IllegalArgumentException.class, () -> ScriptArtifact.external(
                "cutover-check", "3", "display version", sha256("display clock"),
                ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "NONE", null));
    }

    @Test
    void acceptsEveryDeclaredScriptSource() {
        assertEquals(ScriptArtifact.ScriptSource.LOCAL_MANAGED, ScriptArtifact.local(
                "local-check", "1", "show clock", sha256("show clock"),
                ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "NONE", null).source());
        assertEquals(ScriptArtifact.ScriptSource.EXTERNAL_DELIVERED, ScriptArtifact.external(
                "external-check", "1", "show version", sha256("show version"),
                ScriptArtifact.PersistencePolicy.EXECUTION_ONLY, "NONE", null).source());
        assertEquals(ScriptArtifact.ScriptSource.ADHOC_INLINE, ScriptArtifact.adHoc(
                "inline-check", "1", "show interfaces", sha256("show interfaces"),
                "NONE", null).source());
    }

    @Test
    void parserFailureProducesPartialSuccessWithoutDroppingRawOutput() {
        CollectionTarget target = queuedTarget();
        target.startConnecting();
        target.startExecuting();
        target.captureOutput(0, "hostname=core-01", "", false);
        target.startParsing();
        target.partiallySucceed("invalid parser configuration");

        assertEquals(CollectionStatus.PARTIAL_SUCCESS, target.status());
        assertEquals("hostname=core-01", target.standardOutput());
    }

    @Test
    void retainsOutputTruncationState() {
        CollectionTarget target = queuedTarget();
        target.startConnecting();
        target.startExecuting();
        target.captureOutput(0, "partial output", "", true);

        assertTrue(target.outputTruncated());
    }

    @Test
    void rejectsIllegalAndTerminalStateTransitions() {
        CollectionTarget target = queuedTarget();
        assertThrows(IllegalStateException.class, target::startExecuting);

        target.startConnecting();
        target.fail("unreachable");
        assertThrows(IllegalStateException.class, target::startExecuting);
    }

    @Test
    void contextSnapshotDefensivelyCopiesExtensions() {
        Map<String, String> extensions = new java.util.LinkedHashMap<>();
        extensions.put("region", "east");
        CollectionContextSnapshot snapshot = snapshot(extensions);
        extensions.put("region", "west");

        assertEquals(Map.of("region", "east"), snapshot.extensions());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.extensions().put("zone", "a"));
    }

    @Test
    void optionalContextDoesNotCreateProjectOrDevicePlaceholders() {
        CollectionContextSnapshot snapshot = CollectionContextSnapshot.ofOptional(null, null, Map.of());

        assertTrue(snapshot.project().isEmpty());
        assertTrue(snapshot.device().isEmpty());
    }

    @Test
    void rejectsSecretLookingExtensionKeysAndPrivateKeyPayloads() {
        for (String key : List.of("password", "passwd", "pwd", "secret", "private-key", "privateKey",
                "passphrase", "credential", "token", "api-key", "access-key")) {
            assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of(key, "value")), key);
        }
        assertThrows(IllegalArgumentException.class,
                () -> snapshot(Map.of("region", "-----BEGIN PRIVATE KEY-----\nkey-material")));
    }

    @Test
    void rejectsCredentialLookingAndUnknownExtensionKeys() {
        for (String key : List.of("sshPwd", "device-passwd", "auth-token", "vendor-api-key", "maintenanceWindow")) {
            assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of(key, "value")), key);
        }
    }

    @Test
    void rejectsBlankAndOversizedExtensionMetadata() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of(" ", "value")));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of("region", " ")));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of("region", "x".repeat(1025))));
    }

    @Test
    void targetRetainsExactInitiatorSuppliedSnapshotAndNonSecretEndpoint() {
        CollectionContextSnapshot snapshot = snapshot(Map.of("region", "east"));
        CollectionTarget target = CollectionTarget.forSnapshot(snapshot, ConnectionProtocol.SSH2,
                "10.0.0.8", 22, "collector", "SHA256:abc", null);

        assertTrue(target.contextSnapshot() == snapshot);
        CollectionTarget.EndpointSnapshot endpoint = target.endpointSnapshot();
        assertEquals(ConnectionProtocol.SSH2, endpoint.protocol());
        assertEquals("10.0.0.8", endpoint.host());
        assertEquals(22, endpoint.port());
        assertEquals("collector", endpoint.username());
        assertEquals("SHA256:abc", endpoint.hostKeyFingerprint());
        assertTrue(endpoint.telnetPrompts() == null);
    }

    @Test
    void telnetEndpointRetainsPromptsAcrossSubmissionAndRestore() {
        CollectionContextSnapshot snapshot = snapshot(Map.of());
        CommandExecutionPort.TelnetPrompts prompts = CommandExecutionPort.TelnetPrompts.defaults();
        CollectionTarget submitted = CollectionTarget.forSnapshot(snapshot, ConnectionProtocol.TELNET,
                "10.0.0.8", 23, "collector", null, prompts);
        CollectionTarget restored = CollectionTarget.restore(snapshot, ConnectionProtocol.TELNET,
                "10.0.0.8", 23, "collector", null, prompts, CollectionStatus.FAILED,
                "", "", null, "AUTH_FAILED", false);

        assertEquals(ConnectionProtocol.TELNET, submitted.endpointSnapshot().protocol());
        assertEquals(prompts, submitted.endpointSnapshot().telnetPrompts());
        assertEquals(ConnectionProtocol.TELNET, restored.endpointSnapshot().protocol());
        assertEquals(prompts, restored.endpointSnapshot().telnetPrompts());
        assertThrows(IllegalArgumentException.class, () -> CollectionTarget.forSnapshot(snapshot,
                ConnectionProtocol.SSH2, "10.0.0.8", 22, "collector", "SHA256:abc", prompts));
        assertThrows(IllegalArgumentException.class, () -> CollectionTarget.forSnapshot(snapshot,
                ConnectionProtocol.TELNET, "10.0.0.8", 23, "collector", "SHA256:abc", prompts));
        assertThrows(NullPointerException.class, () -> CollectionTarget.forSnapshot(snapshot,
                ConnectionProtocol.TELNET, "10.0.0.8", 23, "collector", null, null));
    }

    @Test
    void collectionTargetExposesNoSecretLookingMemberOrApi() {
        for (var field : CollectionTarget.class.getDeclaredFields()) {
            assertFalse(looksSecret(field.getName()));
        }
        for (var method : CollectionTarget.class.getDeclaredMethods()) {
            assertFalse(looksSecret(method.getName()));
            assertFalse(looksMasterDataOperation(method.getName()));
        }
    }

    @Test
    void taskRequiresAtLeastOneTargetAndDefensivelyCopiesTargets() {
        CollectionTarget target = queuedTarget();
        assertThrows(IllegalArgumentException.class, () -> submittedTask("task-1", List.of()));

        CollectionTask task = submittedTask("task-1", List.of(target));
        assertEquals(List.of(target), task.targets());
        assertEquals("project-42", task.projectKey().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> task.targets().add(queuedTarget()));
    }

    @Test
    void exposesNoPublicFactoryForIncompleteCollectionTasks() {
        assertThrows(NoSuchMethodException.class,
                () -> CollectionTask.class.getMethod("create", String.class, List.class));
    }

    @Test
    void noArgumentTerminalOperationsFollowTheStateContract() {
        CollectionTarget timedOut = queuedTarget();
        timedOut.startConnecting();
        timedOut.timeOut();
        assertEquals(CollectionStatus.TIMED_OUT, timedOut.status());

        CollectionTarget cancelled = queuedTarget();
        cancelled.cancel();
        assertEquals(CollectionStatus.CANCELLED, cancelled.status());
    }

    private static CollectionTarget queuedTarget() {
        return CollectionTarget.forSnapshot(snapshot(Map.of()), ConnectionProtocol.SSH2,
                "10.0.0.8", 22, "collector", null, null);
    }

    private static CollectionContextSnapshot snapshot(Map<String, String> extensions) {
        return CollectionContextSnapshot.of("pms", "project-42", "Delivery", "P42",
                "device-9", "core-01", "Acme", "R9000", extensions);
    }

    private static CollectionTask submittedTask(String taskId, List<CollectionTarget> targets) {
        return CollectionTask.submitted(taskId, "pms", "project-42", "request-1", "idem-1", targets,
                ScriptArtifact.external("cutover-check", "3", "display version", sha256("display version"),
                        ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "NONE", null),
                "DELIVERY_CHECK", java.net.URI.create("https://callback.example.test/collections/request-1"));
    }

    private static boolean looksSecret(String name) {
        String lowerCaseName = name.toLowerCase();
        return lowerCaseName.contains("password") || lowerCaseName.contains("secret")
                || lowerCaseName.contains("privatekey") || lowerCaseName.contains("passphrase")
                || lowerCaseName.contains("credential");
    }

    private static boolean looksMasterDataOperation(String name) {
        String lowerCaseName = name.toLowerCase();
        return lowerCaseName.contains("master") || lowerCaseName.contains("query")
                || lowerCaseName.contains("update") || lowerCaseName.contains("save")
                || lowerCaseName.contains("repository") || lowerCaseName.contains("synchron");
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
