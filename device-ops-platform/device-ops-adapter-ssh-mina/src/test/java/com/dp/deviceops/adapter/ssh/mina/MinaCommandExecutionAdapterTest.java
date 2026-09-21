package com.dp.deviceops.adapter.ssh.mina;

import com.dp.deviceops.core.model.ConnectionFailure;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.core.port.CommandExecutionPort.OutputProgress;
import com.dp.deviceops.core.port.CommandExecutionPort.AuthenticationType;
import com.dp.deviceops.core.port.CommandExecutionPort.CommandResult;
import com.dp.deviceops.core.port.CommandExecutionPort.ConnectionSpec;
import com.dp.deviceops.core.port.CommandExecutionPort.ExecutionMode;
import com.dp.deviceops.core.port.CommandExecutionPort.TelnetPrompts;
import com.dp.deviceops.core.terminal.TerminalTextProcessor;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MinaCommandExecutionAdapterTest {

    private static final String USERNAME = "device-user";
    private static final String PASSWORD = "correct-password";
    private static final String SLOW_AUTH_PASSWORD = "slow-auth-password";
    private static final String SHARED_DEADLINE_PASSWORD = "shared-deadline-password";
    private static final int OUTPUT_LIMIT = 1024 * 1024;
    private static final AtomicReference<ConcurrentSessionGate> CONCURRENT_SESSION_GATE = new AtomicReference<>();
    private static final Consumer<SshClient> KEEP_SHARED_CLIENT_RUNNING = client -> {
    };

    private SshServer server;
    private KeyPair hostKey;
    private KeyPair clientKey;
    private SshClient sharedClient;
    private MinaCommandExecutionAdapter adapter;
    private final AtomicInteger sharedClientStopCalls = new AtomicInteger();

    @BeforeAll
    void startSshServer() throws Exception {
        hostKey = generateKeyPair();
        clientKey = generateKeyPair();
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(sessionContext -> {
            awaitConcurrentHostKey(sessionContext);
            return List.of(hostKey);
        });
        server.setPasswordAuthenticator((username, password, session) -> {
            if (SLOW_AUTH_PASSWORD.equals(password)) {
                try {
                    Thread.sleep(Duration.ofMillis(250));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            if (SHARED_DEADLINE_PASSWORD.equals(password)) {
                try {
                    Thread.sleep(Duration.ofSeconds(3));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return USERNAME.equals(username)
                    && (PASSWORD.equals(password)
                    || SLOW_AUTH_PASSWORD.equals(password)
                    || SHARED_DEADLINE_PASSWORD.equals(password));
        });
        server.setPublickeyAuthenticator((username, key, session) ->
                USERNAME.equals(username) && Arrays.equals(clientKey.getPublic().getEncoded(), key.getEncoded()));
        server.setCommandFactory((channel, command) -> new EmbeddedCommand(command, false));
        server.setShellFactory(channel -> new EmbeddedCommand(null, true));
        server.start();
        sharedClient = MinaCommandExecutionAdapter.createClient();
        adapter = new MinaCommandExecutionAdapter((host, port) -> host, sharedClient, client -> {
            sharedClientStopCalls.incrementAndGet();
            client.stop();
        });
    }

    @AfterEach
    void awaitCommandWorkers() throws InterruptedException {
        EmbeddedCommand.awaitWorkers(Duration.ofSeconds(2));
    }

    @AfterAll
    void stopSshServer() throws IOException, InterruptedException {
        if (adapter != null) {
            adapter.close();
        }
        if (server != null) {
            server.stop(true);
        }
        EmbeddedCommand.awaitWorkers(Duration.ofSeconds(2));
    }

    @Test
    void acceptsOnlyTheConfiguredServerKeyFingerprint() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair trusted = generator.generateKeyPair();
        KeyPair different = generator.generateKeyPair();
        String fingerprint = KeyUtils.getFingerPrint(trusted.getPublic());

        assertTrue(MinaCommandExecutionAdapter.matchesFingerprint(fingerprint, trusted.getPublic()));
        assertFalse(MinaCommandExecutionAdapter.matchesFingerprint(fingerprint, different.getPublic()));
        assertFalse(MinaCommandExecutionAdapter.matchesFingerprint(" ", trusted.getPublic()));
    }

    @Test
    void exposesSsh2ProtocolAndRejectsOtherConnectionSpecs() {
        assertEquals(ConnectionProtocol.SSH2, adapter.protocol());
        ConnectionSpec telnet = new ConnectionSpec(ConnectionProtocol.TELNET, "192.0.2.10", 23, USERNAME,
                AuthenticationType.PASSWORD, ExecutionMode.SHELL, null, TelnetPrompts.defaults(),
                Duration.ofSeconds(3));

        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> adapter.test(telnet, PASSWORD.toCharArray(), new char[0]));

        assertFailure(failure, ConnectionFailure.Code.PROTOCOL_DISABLED, ConnectionFailure.Stage.CONNECT,
                "connection protocol is disabled");
    }

    @Test
    void invalidConfigurationDoesNotCreateOwnedClient() {
        AtomicInteger clientCreations = new AtomicInteger();
        Supplier<SshClient> clientFactory = () -> {
            clientCreations.incrementAndGet();
            return new CountingSshClient();
        };

        assertThrows(NullPointerException.class, () -> new MinaCommandExecutionAdapter(
                null, OUTPUT_LIMIT, MinaCommandExecutionAdapter.DEFAULT_EVENT_CHUNK_BYTES,
                null, TerminalTextProcessor.DEFAULT_MAX_PAGES, clientFactory, SshClient::stop));
        assertThrows(IllegalArgumentException.class, () -> new MinaCommandExecutionAdapter(
                (host, port) -> host, 0, MinaCommandExecutionAdapter.DEFAULT_EVENT_CHUNK_BYTES,
                null, TerminalTextProcessor.DEFAULT_MAX_PAGES, clientFactory, SshClient::stop));
        assertThrows(IllegalArgumentException.class, () -> new MinaCommandExecutionAdapter(
                (host, port) -> host, OUTPUT_LIMIT, 0,
                null, TerminalTextProcessor.DEFAULT_MAX_PAGES, clientFactory, SshClient::stop));
        assertThrows(IllegalArgumentException.class, () -> new MinaCommandExecutionAdapter(
                (host, port) -> host, OUTPUT_LIMIT,
                MinaCommandExecutionAdapter.DEFAULT_EVENT_CHUNK_BYTES + 1,
                null, TerminalTextProcessor.DEFAULT_MAX_PAGES, clientFactory, SshClient::stop));
        assertThrows(IllegalArgumentException.class, () -> new MinaCommandExecutionAdapter(
                (host, port) -> host, OUTPUT_LIMIT, MinaCommandExecutionAdapter.DEFAULT_EVENT_CHUNK_BYTES,
                null, 0, clientFactory, SshClient::stop));

        assertEquals(0, clientCreations.get());
    }

    @Test
    void authenticatedPasswordExecReturnsExitAndOutput() {
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));

        adapter.test(connection, PASSWORD.toCharArray(), new char[0]);
        CommandResult result = adapter.execute(connection, PASSWORD.toCharArray(), new char[0],
                "deterministic", Duration.ofSeconds(3));

        assertEquals(0, result.exitCode());
        assertEquals("deterministic-output\n", result.stdout());
        assertEquals("", result.stderr());
        assertFalse(result.timedOut());
        assertFalse(result.truncated());
        assertTrue(result.durationMillis() >= 0);
    }

    @Test
    void execKeepsEachNormalizedCommandInItsOwnOutputBlock() {
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));
        List<OutputProgress> progress = new ArrayList<>();

        CommandResult result = adapter.execute(connection, PASSWORD.toCharArray(), new char[0],
                " first \r\n\r\nsecond\rthird ", Duration.ofSeconds(3), progress::add);

        assertEquals(List.of("first", "second", "third"), result.commandBlocks().stream()
                .map(CommandOutputBlock::commandText).toList());
        assertEquals(List.of("first-output\n", "second-output\n", "third-output\n"),
                result.commandBlocks().stream().map(CommandOutputBlock::stdout).toList());
        assertTrue(result.commandBlocks().stream()
                .allMatch(block -> block.status() == CommandBlockStatus.SUCCEEDED));
        assertEquals(List.of(1, 2, 3), progress.stream().map(OutputProgress::commandIndex).distinct().toList());
        assertEquals("first-output\nsecond-output\nthird-output\n", result.stdout());
    }

    @Test
    void closeWaitsForActiveCommandAndRejectsLaterCalls() throws Exception {
        MinaCommandExecutionAdapter lifecycleAdapter =
                new MinaCommandExecutionAdapter(
                        (host, port) -> host, sharedClient, KEEP_SHARED_CLIENT_RUNNING);
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch closeInvoked = new CountDownLatch(1);
        EmbeddedCommand.prepareBlockingCommand();
        try {
            Future<CommandResult> execution = executor.submit(() -> lifecycleAdapter.execute(
                    connection, PASSWORD.toCharArray(), new char[0],
                    "blocking-lifecycle", Duration.ofSeconds(5)));
            assertTrue(EmbeddedCommand.awaitBlockingCommandStarted(Duration.ofSeconds(3)));

            Future<?> closing = executor.submit(() -> {
                closeInvoked.countDown();
                lifecycleAdapter.close();
            });
            assertTrue(closeInvoked.await(1, TimeUnit.SECONDS));
            Thread.sleep(Duration.ofMillis(150));
            assertFalse(closing.isDone(), "close completed while an SSH command still held the client");

            EmbeddedCommand.releaseBlockingCommand();
            CommandResult result = execution.get(3, TimeUnit.SECONDS);
            assertEquals(0, result.exitCode());
            assertEquals("blocking-lifecycle-output\n", result.stdout());
            closing.get(3, TimeUnit.SECONDS);

            assertClosed(() -> lifecycleAdapter.test(
                    connection, PASSWORD.toCharArray(), new char[0]));
            assertClosed(() -> lifecycleAdapter.execute(
                    connection, PASSWORD.toCharArray(), new char[0],
                    "after-close", Duration.ofSeconds(3)));
        } finally {
            EmbeddedCommand.releaseBlockingCommand();
            lifecycleAdapter.close();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void forceCloseReturnsWithinBoundAndMakesRepeatedCloseLockFreeWhileCallIsActive() throws Exception {
        AtomicInteger stopCalls = new AtomicInteger();
        MinaCommandExecutionAdapter lifecycleAdapter = new MinaCommandExecutionAdapter(
                (host, port) -> host, sharedClient, ignored -> stopCalls.incrementAndGet());
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        EmbeddedCommand.prepareBlockingCommand();
        try {
            Future<CommandResult> execution = executor.submit(() -> lifecycleAdapter.execute(
                    connection, PASSWORD.toCharArray(), new char[0],
                    "blocking-lifecycle", Duration.ofSeconds(5)));
            assertTrue(EmbeddedCommand.awaitBlockingCommandStarted(Duration.ofSeconds(3)));

            long forceStarted = System.nanoTime();
            lifecycleAdapter.forceClose();
            long forceMillis = Duration.ofNanos(System.nanoTime() - forceStarted).toMillis();
            long closeStarted = System.nanoTime();
            lifecycleAdapter.close();
            long closeMillis = Duration.ofNanos(System.nanoTime() - closeStarted).toMillis();

            assertTrue(forceMillis < 200, () -> "forceClose took " + forceMillis + "ms");
            assertTrue(closeMillis < 200, () -> "repeated close took " + closeMillis + "ms");
            assertEquals(1, stopCalls.get());
            assertFalse(execution.isDone());
            EmbeddedCommand.releaseBlockingCommand();
            assertEquals(0, execution.get(3, TimeUnit.SECONDS).exitCode());
            lifecycleAdapter.close();
            assertEquals(1, stopCalls.get());
        } finally {
            EmbeddedCommand.releaseBlockingCommand();
            lifecycleAdapter.forceClose();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void productionForceCloseUsesImmediateSshClientCloseInsteadOfStopWait() {
        MinaCommandExecutionAdapter owned = new MinaCommandExecutionAdapter(
                (host, port) -> host, 1024 * 1024, 1024, null, 32);
        long started = System.nanoTime();
        owned.forceClose();
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertTrue(elapsedMillis < 500, () -> "production forceClose took " + elapsedMillis + "ms");
        owned.close();
    }

    @Test
    void productionForceCloseRemainsBoundedWithAnActiveEmbeddedSshCommand() throws Exception {
        MinaCommandExecutionAdapter owned = new MinaCommandExecutionAdapter(
                (host, port) -> host, 1024 * 1024, 1024, null, 32);
        ConnectionSpec connection = connection(
                AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        EmbeddedCommand.prepareBlockingCommand();
        try {
            Future<CommandResult> execution = executor.submit(() -> owned.execute(
                    connection, PASSWORD.toCharArray(), new char[0],
                    "blocking-lifecycle", Duration.ofSeconds(10)));
            assertTrue(EmbeddedCommand.awaitBlockingCommandStarted(Duration.ofSeconds(3)));

            long started = System.nanoTime();
            owned.forceClose();
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

            assertTrue(elapsedMillis < 500,
                    () -> "active production forceClose took " + elapsedMillis + "ms");
            try {
                execution.get(3, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException expected) {
                assertTrue(expected.getCause() instanceof RuntimeException);
            }
            assertTrue(execution.isDone());
        } finally {
            EmbeddedCommand.releaseBlockingCommand();
            owned.forceClose();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    @Order(Integer.MAX_VALUE)
    void progressListenerCanCloseAdapterWithoutDeadlock() throws Exception {
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));
        AtomicBoolean closeTriggered = new AtomicBoolean();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Future<CommandResult> execution = executor.submit(() -> adapter.execute(
                connection, PASSWORD.toCharArray(), new char[0],
                "deterministic", Duration.ofSeconds(3), progress -> {
                    if (closeTriggered.compareAndSet(false, true)) {
                        adapter.close();
                    }
                }));
        try {
            CommandResult result = execution.get(3, TimeUnit.SECONDS);
            assertTrue(closeTriggered.get());
            assertEquals(0, result.exitCode());
            assertEquals("deterministic-output\n", result.stdout());
            assertEquals(1, sharedClientStopCalls.get());
            assertClosed(() -> adapter.execute(
                    connection, PASSWORD.toCharArray(), new char[0],
                    "after-callback-close", Duration.ofSeconds(3)));
        } finally {
            if (execution.isDone()) {
                adapter.close();
            }
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSessionsKeepHostKeyPolicyAndOutputIsolated() throws Exception {
        int taskCount = 8;
        ConcurrentSessionGate sessionGate = new ConcurrentSessionGate(taskCount, taskCount / 2);
        assertTrue(CONCURRENT_SESSION_GATE.compareAndSet(null, sessionGate));
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<ConcurrentCommandOutcome>> futures = new ArrayList<>(taskCount);
        try {
            for (int task = 0; task < taskCount; task++) {
                int taskNumber = task;
                boolean trustedHostKey = taskNumber % 2 == 0;
                String command = "concurrent-" + taskNumber;
                String fingerprint = trustedHostKey
                        ? KeyUtils.getFingerPrint(hostKey.getPublic())
                        : KeyUtils.getFingerPrint(clientKey.getPublic());
                ConnectionSpec connection = connection(
                        AuthenticationType.PASSWORD, ExecutionMode.EXEC, fingerprint);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        CommandResult result = adapter.execute(
                                connection, PASSWORD.toCharArray(), new char[0],
                                command, Duration.ofSeconds(5));
                        return new ConcurrentCommandOutcome(result.stdout(), null);
                    } catch (ConnectionFailure failure) {
                        return new ConcurrentCommandOutcome(null, failure);
                    }
                }));
            }

            assertTrue(ready.await(3, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(sessionGate.awaitHostKeySessions(Duration.ofSeconds(5)),
                    () -> "only " + sessionGate.hostKeySessionCount() + " SSH sessions overlapped");
            assertTrue(sessionGate.hostKeySessionCount() >= taskCount,
                    () -> "only " + sessionGate.hostKeySessionCount() + " SSH sessions overlapped");
            assertTrue(futures.stream().noneMatch(Future::isDone),
                    "a session completed before the shared host-key barrier was released");
            sessionGate.releaseHostKeys();

            assertTrue(sessionGate.awaitCommands(Duration.ofSeconds(5)),
                    () -> "only " + sessionGate.commandCount() + " trusted commands overlapped");
            assertEquals(taskCount / 2, sessionGate.commandCount());
            for (int task = 0; task < taskCount; task++) {
                if (task % 2 == 0) {
                    assertFalse(futures.get(task).isDone(),
                            "trusted command completed before the command barrier was released");
                } else {
                    ConcurrentCommandOutcome outcome = futures.get(task).get(5, TimeUnit.SECONDS);
                    assertNull(outcome.stdout());
                    assertFailure(outcome.failure(), ConnectionFailure.Code.HOST_KEY_MISMATCH,
                            ConnectionFailure.Stage.VERIFY_HOST, "host key verification failed");
                }
            }
            sessionGate.releaseCommands();

            for (int task = 0; task < taskCount; task++) {
                ConcurrentCommandOutcome outcome = futures.get(task).get(5, TimeUnit.SECONDS);
                if (task % 2 == 0) {
                    assertEquals("concurrent-" + task + "-output\n", outcome.stdout());
                    assertNull(outcome.failure());
                } else {
                    assertNull(outcome.stdout());
                    assertFailure(outcome.failure(), ConnectionFailure.Code.HOST_KEY_MISMATCH,
                            ConnectionFailure.Stage.VERIFY_HOST, "host key verification failed");
                }
            }
        } finally {
            start.countDown();
            sessionGate.releaseAll();
            CONCURRENT_SESSION_GATE.compareAndSet(sessionGate, null);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void repeatedCloseIsSafeAndKeepsAdapterClosed() {
        CountingSshClient countingClient = new CountingSshClient();
        MinaCommandExecutionAdapter closeAdapter =
                new MinaCommandExecutionAdapter((host, port) -> host, countingClient, SshClient::stop);
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));

        assertDoesNotThrow(closeAdapter::close);
        assertDoesNotThrow(closeAdapter::close);
        assertEquals(1, countingClient.stopCalls());
        assertClosed(() -> closeAdapter.test(connection, PASSWORD.toCharArray(), new char[0]));
    }

    @Test
    void wrongFingerprintFailsBeforeCommandExecution() {
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(clientKey.getPublic()));

        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> adapter.execute(connection, PASSWORD.toCharArray(), new char[0],
                        "deterministic", Duration.ofSeconds(3)));

        assertFailure(failure, ConnectionFailure.Code.HOST_KEY_MISMATCH,
                ConnectionFailure.Stage.VERIFY_HOST, "host key verification failed");
        assertTrue(failure.getCause() != null);
    }

    @Test
    void wrongPasswordFailsWithoutEchoingCredential() {
        String rejectedPassword = "wrong-password-sensitive";
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));

        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> adapter.execute(connection, rejectedPassword.toCharArray(), new char[0],
                        "deterministic", Duration.ofSeconds(3)));

        assertFailure(failure, ConnectionFailure.Code.AUTH_FAILED,
                ConnectionFailure.Stage.AUTHENTICATE, "authentication failed");
        assertTrue(failure.getCause() != null);
        assertFalse(failure.getMessage().contains(rejectedPassword));
    }

    @Test
    void endpointResolutionFailureMapsToUnreachableResolve() {
        MinaCommandExecutionAdapter rejectingAdapter = new MinaCommandExecutionAdapter((host, port) -> {
            throw new IllegalArgumentException("resolver-sensitive-detail");
        }, sharedClient, KEEP_SHARED_CLIENT_RUNNING);
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));

        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> rejectingAdapter.test(connection, PASSWORD.toCharArray(), new char[0]));

        assertFailure(failure, ConnectionFailure.Code.UNREACHABLE,
                ConnectionFailure.Stage.RESOLVE, "remote endpoint is unreachable");
        assertTrue(failure.getCause() != null);
        rejectingAdapter.close();
    }

    @Test
    void exhaustedHandshakeDeadlineMapsToConnectTimeout() {
        MinaCommandExecutionAdapter delayedResolutionAdapter = new MinaCommandExecutionAdapter((host, port) -> {
            try {
                Thread.sleep(Duration.ofMillis(100));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return host;
        }, sharedClient, KEEP_SHARED_CLIENT_RUNNING);
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()), Duration.ofMillis(50));

        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> delayedResolutionAdapter.test(connection, PASSWORD.toCharArray(), new char[0]));

        assertFailure(failure, ConnectionFailure.Code.CONNECT_TIMEOUT,
                ConnectionFailure.Stage.CONNECT, "connection timed out");
        assertTrue(failure.getCause() != null);
        delayedResolutionAdapter.close();
    }

    @Test
    void commandTimeoutReturnsTimedOutResultAndMinusOneExit() {
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));

        CommandResult result = adapter.execute(connection, PASSWORD.toCharArray(), new char[0],
                "timeout", Duration.ofMillis(150));

        assertEquals(-1, result.exitCode());
        assertTrue(result.timedOut());
        assertTrue(result.durationMillis() >= 100);
    }

    @Test
    void commandTimeoutStartsAfterAuthenticationCompletes() {
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));

        CommandResult result = adapter.execute(connection, SLOW_AUTH_PASSWORD.toCharArray(), new char[0],
                "deterministic", Duration.ofMillis(150));

        assertEquals(0, result.exitCode());
        assertFalse(result.timedOut());
    }

    @Test
    void connectionAndAuthenticationShareOneDeadline() {
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()), Duration.ofSeconds(1));
        long started = System.nanoTime();

        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> adapter.test(connection, SHARED_DEADLINE_PASSWORD.toCharArray(), new char[0]));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertFailure(failure, ConnectionFailure.Code.CONNECT_TIMEOUT,
                ConnectionFailure.Stage.CONNECT, "connection timed out");
        assertTrue(elapsedMillis < 1600, () -> "connection deadline took " + elapsedMillis + " ms");
    }

    @Test
    void operationDeadlineDoesNotRefreshBetweenConnectionPhases() {
        AtomicLong now = new AtomicLong();
        MinaCommandExecutionAdapter.OperationDeadline deadline =
                new MinaCommandExecutionAdapter.OperationDeadline(Duration.ofSeconds(5), now::get);

        assertEquals(Duration.ofSeconds(5), deadline.remaining());
        now.set(Duration.ofSeconds(3).toNanos());
        assertEquals(Duration.ofSeconds(2), deadline.remaining());
        now.set(Duration.ofSeconds(6).toNanos());
        assertEquals(Duration.ZERO, deadline.remaining());
    }

    @Test
    void commandTimeoutDoesNotWaitForRemoteTeardown() {
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));
        long started = System.nanoTime();

        CommandResult result = adapter.execute(connection, PASSWORD.toCharArray(), new char[0],
                "stubborn-timeout", Duration.ofMillis(100));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        long cleanupMillis = elapsedMillis - result.durationMillis();

        assertTrue(result.timedOut());
        assertEquals(-1, result.exitCode());
        assertTrue(result.durationMillis() < 800,
                () -> "command deadline took " + result.durationMillis() + " ms");
        assertTrue(elapsedMillis < 800, () -> "timeout cleanup took " + elapsedMillis + " ms");
        assertTrue(cleanupMillis < 250, () -> "post-result cleanup took " + cleanupMillis + " ms");
    }

    @Test
    void boundsCombinedOutputToConfiguredBudget() {
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));
        MinaCommandExecutionAdapter boundedAdapter = new MinaCommandExecutionAdapter(
                (host, port) -> host, OUTPUT_LIMIT,
                MinaCommandExecutionAdapter.DEFAULT_EVENT_CHUNK_BYTES, null, 100,
                sharedClient, KEEP_SHARED_CLIENT_RUNNING);

        CommandResult result = boundedAdapter.execute(connection, PASSWORD.toCharArray(), new char[0],
                "large-output", Duration.ofSeconds(10));

        assertEquals(0, result.exitCode());
        assertEquals(OUTPUT_LIMIT,
                result.stdout().getBytes(StandardCharsets.UTF_8).length
                        + result.stderr().getBytes(StandardCharsets.UTF_8).length);
        assertTrue(result.truncated());
        boundedAdapter.close();
    }

    @Test
    void decodesUtf8StdoutAndStderr() {
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));

        CommandResult result = adapter.execute(connection, PASSWORD.toCharArray(), new char[0],
                "utf8-output", Duration.ofSeconds(3));

        assertEquals(0, result.exitCode());
        assertEquals("设备联通\n", result.stdout());
        assertEquals("诊断正常\n", result.stderr());
    }

    @Test
    void shellNormalizesNewlinesPreservesTranscriptAndDoesNotSendExit() {
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.SHELL,
                KeyUtils.getFingerPrint(hostKey.getPublic()));

        CommandResult result = adapter.execute(connection, PASSWORD.toCharArray(), new char[0],
                "show\r\nclock\rstatus", Duration.ofSeconds(3));

        assertEquals(0, result.exitCode());
        assertEquals("show\ndevice#clock\ndevice#status\ndevice#", result.stdout());
    }

    @Test
    void shellWithoutRemoteExitStatusMapsToConnectionClosed() {
        ConnectionSpec connection = connection(AuthenticationType.PASSWORD, ExecutionMode.SHELL,
                KeyUtils.getFingerPrint(hostKey.getPublic()));

        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> adapter.execute(connection, PASSWORD.toCharArray(), new char[0],
                        "close-without-status", Duration.ofSeconds(3)));

        assertFailure(failure, ConnectionFailure.Code.CONNECTION_CLOSED,
                ConnectionFailure.Stage.EXECUTE, "connection closed during command execution");
        assertTrue(failure.getCause() != null);
    }

    @Test
    void authenticatesWithPrivateKeyMaterial() {
        ConnectionSpec connection = connection(AuthenticationType.PRIVATE_KEY, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));
        char[] privateKey = pem(clientKey).toCharArray();

        CommandResult result = adapter.execute(connection, privateKey, new char[0],
                "deterministic", Duration.ofSeconds(3));

        assertEquals(0, result.exitCode());
        assertEquals("deterministic-output\n", result.stdout());
    }

    @Test
    void invalidPrivateKeyMapsToAuthenticationFailure() {
        ConnectionSpec connection = connection(AuthenticationType.PRIVATE_KEY, ExecutionMode.EXEC,
                KeyUtils.getFingerPrint(hostKey.getPublic()));

        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> adapter.execute(connection, "not-a-private-key".toCharArray(), new char[0],
                        "deterministic", Duration.ofSeconds(3)));

        assertFailure(failure, ConnectionFailure.Code.AUTH_FAILED,
                ConnectionFailure.Stage.AUTHENTICATE, "authentication failed");
        assertTrue(failure.getCause() != null);
    }

    private ConnectionSpec connection(AuthenticationType authenticationType, ExecutionMode executionMode,
                                      String fingerprint) {
        return connection(authenticationType, executionMode, fingerprint, Duration.ofSeconds(3));
    }

    private ConnectionSpec connection(AuthenticationType authenticationType, ExecutionMode executionMode,
                                      String fingerprint, Duration connectTimeout) {
        return new ConnectionSpec("127.0.0.1", server.getPort(), USERNAME, authenticationType, executionMode,
                fingerprint, connectTimeout);
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String pem(KeyPair keyPair) {
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }

    private static void awaitConcurrentHostKey(SessionContext sessionContext) throws IOException {
        ConcurrentSessionGate gate = CONCURRENT_SESSION_GATE.get();
        if (gate != null) {
            gate.arriveAtHostKey(sessionContext);
        }
    }

    private static void assertFailure(ConnectionFailure failure, ConnectionFailure.Code code,
                                      ConnectionFailure.Stage stage, String safeMessage) {
        assertEquals(code, failure.code());
        assertEquals(stage, failure.stage());
        assertEquals(safeMessage, failure.safeMessage());
        assertEquals(safeMessage, failure.getMessage());
    }

    private static void assertClosed(org.junit.jupiter.api.function.Executable operation) {
        ConnectionFailure failure = assertThrows(ConnectionFailure.class, operation);
        assertFailure(failure, ConnectionFailure.Code.CONNECTION_CLOSED,
                ConnectionFailure.Stage.CONNECT, "connection closed during command execution");
        assertNull(failure.getCause());
    }

    private record ConcurrentCommandOutcome(String stdout, ConnectionFailure failure) {
    }

    private static final class EmbeddedCommand implements Command {

        private static final Set<Thread> ACTIVE_WORKERS = ConcurrentHashMap.newKeySet();
        private static final AtomicReference<BlockingCommandGate> BLOCKING_COMMAND_GATE = new AtomicReference<>();
        private final String command;
        private final boolean shell;
        private final AtomicReference<Thread> worker = new AtomicReference<>();
        private ChannelSession channel;
        private InputStream input;
        private OutputStream output;
        private OutputStream error;
        private ExitCallback exitCallback;

        private EmbeddedCommand(String command, boolean shell) {
            this.command = command;
            this.shell = shell;
        }

        @Override
        public void setInputStream(InputStream input) {
            this.input = input;
        }

        @Override
        public void setOutputStream(OutputStream output) {
            this.output = output;
        }

        @Override
        public void setErrorStream(OutputStream error) {
            this.error = error;
        }

        @Override
        public void setExitCallback(ExitCallback exitCallback) {
            this.exitCallback = exitCallback;
        }

        @Override
        public void start(ChannelSession channel, Environment environment) {
            this.channel = channel;
            Thread commandThread = Thread.ofVirtual().name("embedded-ssh-command").unstarted(this::run);
            worker.set(commandThread);
            ACTIVE_WORKERS.add(commandThread);
            commandThread.start();
        }

        private void run() {
            try {
                if (shell) {
                    runInteractiveShell();
                } else if ("timeout".equals(command)) {
                    Thread.sleep(Duration.ofSeconds(30));
                } else if ("stubborn-timeout".equals(command)) {
                    ignoreInterruptsFor(Duration.ofSeconds(1));
                    return;
                } else if ("blocking-lifecycle".equals(command)) {
                    BlockingCommandGate gate = BLOCKING_COMMAND_GATE.get();
                    if (gate == null) {
                        throw new IOException("blocking command gate is not prepared");
                    }
                    gate.started().countDown();
                    gate.release().await();
                    output.write("blocking-lifecycle-output\n".getBytes(StandardCharsets.UTF_8));
                } else if ("large-output".equals(command)) {
                    byte[] block = new byte[OUTPUT_LIMIT];
                    Arrays.fill(block, (byte) 'x');
                    output.write(block);
                    output.write(new byte[16]);
                    Arrays.fill(block, (byte) 'e');
                    error.write(block);
                    for (int i = 0; i < 16; i++) {
                        error.write('e');
                    }
                } else if ("utf8-output".equals(command)) {
                    output.write("设备联通\n".getBytes(StandardCharsets.UTF_8));
                    error.write("诊断正常\n".getBytes(StandardCharsets.UTF_8));
                } else if ("deterministic".equals(command)) {
                    output.write("deterministic-output\n".getBytes(StandardCharsets.UTF_8));
                } else {
                    ConcurrentSessionGate gate = CONCURRENT_SESSION_GATE.get();
                    if (gate != null && command.startsWith("concurrent-")) {
                        gate.arriveAtCommand(command);
                    }
                    output.write((command + "-output\n").getBytes(StandardCharsets.UTF_8));
                }
                output.flush();
                error.flush();
                exitCallback.onExit(0);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException exception) {
                exitCallback.onExit(1, "embedded command failed");
            } finally {
                ACTIVE_WORKERS.remove(Thread.currentThread());
            }
        }

        private void runInteractiveShell() throws IOException {
            output.write("device#".getBytes(StandardCharsets.UTF_8));
            output.flush();
            StringBuilder commandLine = new StringBuilder();
            int value;
            while ((value = input.read()) >= 0) {
                if (value == '\r') {
                    continue;
                }
                if (value != '\n') {
                    commandLine.append((char) value);
                    continue;
                }
                String received = commandLine.toString();
                commandLine.setLength(0);
                if ("close-without-status".equals(received)) {
                    channel.close(false);
                    return;
                }
                output.write((received + "\n" + "device#").getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
        }

        @Override
        public void destroy(ChannelSession channel) throws InterruptedException {
            Thread commandThread = worker.getAndSet(null);
            if (commandThread != null) {
                commandThread.interrupt();
                commandThread.join(Duration.ofMillis(1500));
            }
        }

        private static void ignoreInterruptsFor(Duration duration) {
            long deadline = System.nanoTime() + duration.toNanos();
            while (System.nanoTime() < deadline) {
                long remainingMillis = Math.max(1,
                        Duration.ofNanos(deadline - System.nanoTime()).toMillis());
                try {
                    Thread.sleep(Math.min(remainingMillis, 50));
                } catch (InterruptedException ignored) {
                    // This simulated device intentionally delays channel teardown.
                }
            }
        }

        private static void awaitWorkers(Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            for (Thread commandThread : java.util.List.copyOf(ACTIVE_WORKERS)) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    break;
                }
                commandThread.join(Duration.ofNanos(remainingNanos));
            }
            if (!ACTIVE_WORKERS.isEmpty()) {
                throw new AssertionError("embedded SSH command workers did not terminate");
            }
        }

        private static void prepareBlockingCommand() {
            if (!BLOCKING_COMMAND_GATE.compareAndSet(null,
                    new BlockingCommandGate(new CountDownLatch(1), new CountDownLatch(1)))) {
                throw new IllegalStateException("blocking command gate is already prepared");
            }
        }

        private static boolean awaitBlockingCommandStarted(Duration timeout) throws InterruptedException {
            BlockingCommandGate gate = BLOCKING_COMMAND_GATE.get();
            return gate != null && gate.started().await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private static void releaseBlockingCommand() {
            BlockingCommandGate gate = BLOCKING_COMMAND_GATE.getAndSet(null);
            if (gate != null) {
                gate.release().countDown();
            }
        }
    }

    private record BlockingCommandGate(CountDownLatch started, CountDownLatch release) {
    }

    private static final class CountingSshClient extends SshClient {

        private final AtomicInteger stopCalls = new AtomicInteger();

        @Override
        public void stop() {
            stopCalls.incrementAndGet();
        }

        private int stopCalls() {
            return stopCalls.get();
        }
    }

    private static final class ConcurrentSessionGate {

        private final Set<SessionContext> hostKeySessions = ConcurrentHashMap.newKeySet();
        private final Set<String> commands = ConcurrentHashMap.newKeySet();
        private final CountDownLatch hostKeyArrivals;
        private final CountDownLatch commandArrivals;
        private final CountDownLatch hostKeyRelease = new CountDownLatch(1);
        private final CountDownLatch commandRelease = new CountDownLatch(1);

        private ConcurrentSessionGate(int expectedHostKeySessions, int expectedCommands) {
            hostKeyArrivals = new CountDownLatch(expectedHostKeySessions);
            commandArrivals = new CountDownLatch(expectedCommands);
        }

        private void arriveAtHostKey(SessionContext sessionContext) throws IOException {
            if (hostKeySessions.add(sessionContext)) {
                hostKeyArrivals.countDown();
            }
            awaitRelease(hostKeyRelease, "interrupted at host-key concurrency barrier");
        }

        private void arriveAtCommand(String command) throws InterruptedException {
            if (commands.add(command)) {
                commandArrivals.countDown();
            }
            commandRelease.await();
        }

        private boolean awaitHostKeySessions(Duration timeout) throws InterruptedException {
            return hostKeyArrivals.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private boolean awaitCommands(Duration timeout) throws InterruptedException {
            return commandArrivals.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private int hostKeySessionCount() {
            return hostKeySessions.size();
        }

        private int commandCount() {
            return commands.size();
        }

        private void releaseHostKeys() {
            hostKeyRelease.countDown();
        }

        private void releaseCommands() {
            commandRelease.countDown();
        }

        private void releaseAll() {
            releaseHostKeys();
            releaseCommands();
        }

        private static void awaitRelease(CountDownLatch release, String message) throws IOException {
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(message, exception);
            }
        }
    }
}
