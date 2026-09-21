package com.dp.deviceops.core.service;

import com.dp.deviceops.core.model.*;
import com.dp.deviceops.core.port.CollectionExecutionPersistencePort;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.OutputParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CollectionWorkerTest {
    @Test void shellCommandRejectionFailsDespiteZeroTransportExitCode() throws Exception {
        List<CollectionStatus> statuses=new ArrayList<>();
        List<String> outcomes=new ArrayList<>();
        var persistence=recordingPersistence(statuses,outcomes);
        CommandExecutionPort command=new CommandExecutionPort() {
            public void test(ConnectionSpec c,char[] s,char[] p) { }
            public CommandResult execute(ConnectionSpec c,char[] s,char[] p,String script,Duration timeout) {
                String output="echo ok\r\n% Unknown command.\r\n<device>";
                var block=new CommandOutputBlock(1,script,CommandBlockStatus.SUCCEEDED,output,"",output.length(),
                        0,false,0,null,Map.of(),List.of(),null,null,false);
                return new CommandResult(0,output,"",false,false,1,List.of(block));
            }
        };
        new CollectionWorker(command,persistence,Clock.systemUTC()).execute(workItem(CommandExecutionPort.ExecutionMode.SHELL));
        assertEquals(CollectionStatus.FAILED,statuses.getLast());
        assertEquals("COMMAND_REJECTED",outcomes.getLast());
        assertFalse(statuses.contains(CollectionStatus.PARSING));
    }
    @Test void claimsExecutesOnlyFrozenContextAndPersistsParsedFacts() throws Exception {
        List<CollectionStatus> statuses = new ArrayList<>();
        List<Map<String, String>> facts = new ArrayList<>();
        CollectionExecutionPersistencePort persistence = new CollectionExecutionPersistencePort() {
            public boolean claim(long id, String worker, Instant started, Instant lease) { return true; }
            public void renewLease(long id, String worker, Instant lease) { }
            public int failUnclaimedTargets(List<Long> ids, String reason) { return 0; }
            public long appendOutput(long id, String worker, int commandIndex,
                                     CommandExecutionPort.OutputStreamType streamType,
                                     String content, long receivedBytes, int pageCount, boolean truncated,
                                     Instant createdAt) { return 1; }
            public void updateTarget(long id, String worker, CollectionStatus status, String out, String err, Integer exit, String outcome, boolean truncated, Map<String, String> values) { statuses.add(status); facts.add(values); }
            public int failRecoveredTargets(Instant now, String reason) { return 0; }
        };
        CommandExecutionPort command = new CommandExecutionPort() {
            public void test(ConnectionSpec connection, char[] secret, char[] passphrase) { }
            public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase, String script, Duration timeout) { assertEquals("frozen-host", connection.host()); assertEquals("secret", String.valueOf(secret)); return new CommandResult(0, "ok", "", false, false, 1, List.of(CommandOutputBlock.legacy("ok", "", 0, false, Map.of(), null))); }
        };
        String script = "echo ok";
        ScriptArtifact artifact = ScriptArtifact.adHoc("one", "v1", script, hex(script), "plain", "");
        ExecutionConnectionContext context = new ExecutionConnectionContext(new CommandExecutionPort.ConnectionSpec("frozen-host", 22, "u", CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.EXEC, "SHA256:a", Duration.ofSeconds(1)), new TransientCredential("secret".toCharArray(), null));
        OutputParser parser = new OutputParser() { public String type() { return "plain"; } public ParseResult parse(String raw, String config) { return new ParseResult(Map.of("state", raw), List.of()); } };
        new CollectionWorker(command, persistence, Clock.systemUTC()).execute(new CollectionWorker.WorkItem(1, "worker", context, artifact, Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofSeconds(1), new OutputParserRegistry(List.of(parser))));
        assertEquals(List.of(CollectionStatus.CONNECTING, CollectionStatus.EXECUTING, CollectionStatus.PARSING, CollectionStatus.SUCCEEDED), statuses);
        assertEquals("ok", facts.getLast().get("command.1.state"));
        assertTrue(context.toString().contains("ExecutionConnectionContext"));
    }

    @Test void persistsOnlyAuthenticationFailureCode() throws Exception {
        List<CollectionStatus> statuses = new ArrayList<>();
        List<String> outcomes = new ArrayList<>();
        CollectionExecutionPersistencePort persistence = recordingPersistence(statuses, outcomes);
        CommandExecutionPort command = new CommandExecutionPort() {
            public void test(ConnectionSpec connection, char[] secret, char[] passphrase) { }
            public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase, String script,
                                         Duration timeout) {
                throw new ConnectionFailure(ConnectionFailure.Code.AUTH_FAILED,
                        ConnectionFailure.Stage.AUTHENTICATE, "authentication rejected",
                        new IllegalStateException("server disclosed a secret"));
            }
        };

        execute(command, persistence);

        assertEquals(List.of(CollectionStatus.CONNECTING, CollectionStatus.FAILED), statuses);
        assertEquals("AUTH_FAILED", outcomes.getLast());
    }

    @Test void interruptedExecutionDefersTerminalizationToTheDispatcherWrapper() throws Exception {
        List<CollectionStatus> statuses = new ArrayList<>();
        List<String> outcomes = new ArrayList<>();
        CollectionExecutionPersistencePort persistence = recordingPersistence(statuses, outcomes);
        CommandExecutionPort command = new CommandExecutionPort() {
            public void test(ConnectionSpec connection, char[] secret, char[] passphrase) { }
            public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase, String script,
                                         Duration timeout) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("forced cancellation");
            }
        };

        try {
            assertThrows(IllegalStateException.class, () -> execute(command, persistence));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(List.of(CollectionStatus.CONNECTING), statuses);
        } finally {
            Thread.interrupted();
        }
    }

    @Test void persistsExecutionTimeoutCodeForTimedOutCommandResult() throws Exception {
        List<CollectionStatus> statuses = new ArrayList<>();
        List<String> outcomes = new ArrayList<>();
        CollectionExecutionPersistencePort persistence = recordingPersistence(statuses, outcomes);
        CommandExecutionPort command = new CommandExecutionPort() {
            public void test(ConnectionSpec connection, char[] secret, char[] passphrase) { }
            public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase, String script,
                                         Duration timeout) {
                return new CommandResult(-1, "", "", true, false, 1,
                        List.of(CommandOutputBlock.legacy("", "", -1, false, Map.of(), "timed out")));
            }
        };

        execute(command, persistence);

        assertEquals(CollectionStatus.TIMED_OUT, statuses.getLast());
        assertEquals("EXECUTION_TIMEOUT", outcomes.getLast());
    }

    private static CollectionExecutionPersistencePort recordingPersistence(List<CollectionStatus> statuses,
                                                                           List<String> outcomes) {
        return new CollectionExecutionPersistencePort() {
            public boolean claim(long id, String worker, Instant started, Instant lease) { return true; }
            public long appendOutput(long id, String worker, int commandIndex, CommandExecutionPort.OutputStreamType type,
                                     String content, long bytes, int pages, boolean truncated, Instant createdAt) { return 1L; }
            public void renewLease(long id, String worker, Instant lease) { }
            public int failUnclaimedTargets(List<Long> ids, String reason) { return 0; }
            public void updateTarget(long id, String worker, CollectionStatus status, String out, String err,
                                     Integer exit, String outcome, boolean truncated, Map<String, String> values) {
                statuses.add(status);
                outcomes.add(outcome);
            }
            public int failRecoveredTargets(Instant now, String reason) { return 0; }
        };
    }

    private static void execute(CommandExecutionPort command, CollectionExecutionPersistencePort persistence)
            throws Exception {
        new CollectionWorker(command, persistence, Clock.systemUTC())
                .execute(workItem());
    }

    private static CollectionWorker.WorkItem workItem() throws Exception {
        return workItem(CommandExecutionPort.ExecutionMode.EXEC);
    }
    private static CollectionWorker.WorkItem workItem(CommandExecutionPort.ExecutionMode mode) throws Exception {
        String script = "echo ok";
        ExecutionConnectionContext context = new ExecutionConnectionContext(
                new CommandExecutionPort.ConnectionSpec("host", 22, "u",
                        CommandExecutionPort.AuthenticationType.PASSWORD,
                        mode, "SHA256:a", Duration.ofSeconds(1)),
                new TransientCredential("secret".toCharArray(), null));
        ScriptArtifact artifact = ScriptArtifact.adHoc("one", "v1", script, hex(script), "plain", "");
        return new CollectionWorker.WorkItem(1, "worker", context, artifact, Duration.ofSeconds(2),
                Duration.ofSeconds(1), Duration.ofSeconds(1), new OutputParserRegistry(List.of()));
    }

    private static String hex(String value) throws Exception { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
}
