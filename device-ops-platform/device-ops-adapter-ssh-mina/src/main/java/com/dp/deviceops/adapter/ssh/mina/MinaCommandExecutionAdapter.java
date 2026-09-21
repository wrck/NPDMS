package com.dp.deviceops.adapter.ssh.mina;

import com.dp.deviceops.core.model.ConnectionFailure;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.core.model.CommandPlan;
import com.dp.deviceops.core.model.CommandPlan.CommandSpec;
import com.dp.deviceops.core.port.ProtocolCommandExecutionAdapter;
import com.dp.deviceops.core.port.RemoteEndpointPolicy;
import com.dp.deviceops.core.terminal.TerminalPagerController;
import com.dp.deviceops.core.terminal.TerminalTextProcessor;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.channel.StreamingChannel;
import org.apache.sshd.common.channel.PtyChannelConfiguration;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.io.IoInputStream;
import org.apache.sshd.common.io.IoOutputStream;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.security.SecurityUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

public final class MinaCommandExecutionAdapter implements ProtocolCommandExecutionAdapter, AutoCloseable {

    static final int DEFAULT_MAX_OUTPUT_BYTES = 8 * 1024 * 1024;
    static final int DEFAULT_EVENT_CHUNK_BYTES = 8_192;
    private static final Duration PAGER_TERMINAL_SETTLE = Duration.ofMillis(250);
    private static final Pattern INITIAL_PROMPT_CANDIDATE =
            Pattern.compile("(?m)(?:^|\\n)[^\\r\\n]*[>#$][ \\t]*\\r?$");

    private static final String PROTOCOL_DISABLED_MESSAGE = "connection protocol is disabled";
    private static final String UNREACHABLE_MESSAGE = "remote endpoint is unreachable";
    private static final String CONNECT_TIMEOUT_MESSAGE = "connection timed out";
    private static final String HOST_KEY_MISMATCH_MESSAGE = "host key verification failed";
    private static final String AUTH_FAILED_MESSAGE = "authentication failed";
    private static final String CONNECTION_CLOSED_MESSAGE = "connection closed during command execution";
    private static final System.Logger LOG = System.getLogger(MinaCommandExecutionAdapter.class.getName());
    private static final AttributeRepository.AttributeKey<HostKeyPolicy> HOST_KEY_POLICY =
            new AttributeRepository.AttributeKey<>();

    private final RemoteEndpointPolicy endpointPolicy;
    private final SshClient client;
    private final int maxOutputBytes;
    private final int eventChunkBytes;
    private final Pattern commandPromptOverride;
    private final int maxPages;
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock(true);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean clientStopped = new AtomicBoolean();
    private final Consumer<SshClient> clientStopper;
    private final Consumer<SshClient> clientForceStopper;

    public MinaCommandExecutionAdapter(RemoteEndpointPolicy endpointPolicy) {
        this(endpointPolicy, DEFAULT_MAX_OUTPUT_BYTES, DEFAULT_EVENT_CHUNK_BYTES,
                null, TerminalTextProcessor.DEFAULT_MAX_PAGES);
    }

    public MinaCommandExecutionAdapter(
            RemoteEndpointPolicy endpointPolicy,
            int maxOutputBytes,
            int eventChunkBytes,
            Pattern commandPromptOverride,
            int maxPages) {
        this(validateConfiguration(endpointPolicy, maxOutputBytes, eventChunkBytes,
                commandPromptOverride, maxPages), MinaCommandExecutionAdapter::createClient,
                SshClient::stop, client -> client.close(true));
    }

    MinaCommandExecutionAdapter(RemoteEndpointPolicy endpointPolicy, SshClient client,
                                Consumer<SshClient> clientStopper) {
        this(validateConfiguration(endpointPolicy, DEFAULT_MAX_OUTPUT_BYTES, DEFAULT_EVENT_CHUNK_BYTES,
                null, TerminalTextProcessor.DEFAULT_MAX_PAGES), client, clientStopper, clientStopper);
    }

    MinaCommandExecutionAdapter(RemoteEndpointPolicy endpointPolicy, SshClient client,
                                 Consumer<SshClient> clientStopper,
                                 Consumer<SshClient> clientForceStopper) {
        this(validateConfiguration(endpointPolicy, DEFAULT_MAX_OUTPUT_BYTES, DEFAULT_EVENT_CHUNK_BYTES,
                null, TerminalTextProcessor.DEFAULT_MAX_PAGES),
                client, clientStopper, clientForceStopper);
    }

    MinaCommandExecutionAdapter(
            RemoteEndpointPolicy endpointPolicy,
            int maxOutputBytes,
            int eventChunkBytes,
            Pattern commandPromptOverride,
            int maxPages,
            SshClient client,
            Consumer<SshClient> clientStopper) {
        this(validateConfiguration(endpointPolicy, maxOutputBytes, eventChunkBytes,
                commandPromptOverride, maxPages), client, clientStopper, clientStopper);
    }

    MinaCommandExecutionAdapter(
            RemoteEndpointPolicy endpointPolicy,
            int maxOutputBytes,
            int eventChunkBytes,
            Pattern commandPromptOverride,
            int maxPages,
            Supplier<SshClient> ownedClientFactory,
            Consumer<SshClient> clientStopper) {
        this(validateConfiguration(endpointPolicy, maxOutputBytes, eventChunkBytes,
                commandPromptOverride, maxPages), ownedClientFactory, clientStopper, clientStopper);
    }

    private MinaCommandExecutionAdapter(AdapterConfiguration configuration,
                                         Supplier<SshClient> ownedClientFactory,
                                         Consumer<SshClient> clientStopper,
                                         Consumer<SshClient> clientForceStopper) {
        this(configuration, createOwnedClient(ownedClientFactory, clientStopper),
                clientStopper, clientForceStopper);
    }

    private MinaCommandExecutionAdapter(AdapterConfiguration configuration,
                                         SshClient client,
                                         Consumer<SshClient> clientStopper,
                                         Consumer<SshClient> clientForceStopper) {
        this.endpointPolicy = configuration.endpointPolicy();
        this.client = Objects.requireNonNull(client, "client");
        this.clientStopper = Objects.requireNonNull(clientStopper, "clientStopper");
        this.clientForceStopper = Objects.requireNonNull(clientForceStopper, "clientForceStopper");
        this.maxOutputBytes = configuration.maxOutputBytes();
        this.eventChunkBytes = configuration.eventChunkBytes();
        this.commandPromptOverride = configuration.commandPromptOverride();
        this.maxPages = configuration.maxPages();
    }

    private static AdapterConfiguration validateConfiguration(
            RemoteEndpointPolicy endpointPolicy,
            int maxOutputBytes,
            int eventChunkBytes,
            Pattern commandPromptOverride,
            int maxPages) {
        Objects.requireNonNull(endpointPolicy, "endpointPolicy");
        if (maxOutputBytes < 1) {
            throw new IllegalArgumentException("maxOutputBytes must be positive");
        }
        if (eventChunkBytes < 1 || eventChunkBytes > DEFAULT_EVENT_CHUNK_BYTES) {
            throw new IllegalArgumentException("eventChunkBytes must be between 1 and 8192");
        }
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must be positive");
        }
        return new AdapterConfiguration(endpointPolicy, maxOutputBytes, eventChunkBytes,
                commandPromptOverride, maxPages);
    }

    private static SshClient createOwnedClient(Supplier<SshClient> ownedClientFactory,
                                               Consumer<SshClient> clientStopper) {
        Objects.requireNonNull(clientStopper, "clientStopper");
        return Objects.requireNonNull(
                Objects.requireNonNull(ownedClientFactory, "ownedClientFactory").get(), "client");
    }

    @Override
    public ConnectionProtocol protocol() {
        return ConnectionProtocol.SSH2;
    }

    @Override
    public void test(ConnectionSpec connection, char[] secret, char[] passphrase) {
        lifecycleLock.readLock().lock();
        boolean admitted = false;
        try {
            requireOpen();
            admitted = true;
            Objects.requireNonNull(connection, "connection");
            requireSsh2(connection);
            char[] secretCopy = copyRequiredSecret(secret);
            char[] passphraseCopy = copyOptional(passphrase);
            ClientSession session = null;
            AtomicBoolean hostKeyMismatch = new AtomicBoolean();
            OperationDeadline connectionDeadline =
                    new OperationDeadline(connection.connectTimeout(), System::nanoTime);
            try {
                session = openSession(client, connection, connectionDeadline, hostKeyMismatch);
                authenticate(session, connection, secretCopy, passphraseCopy, connectionDeadline, hostKeyMismatch);
            } finally {
                closeGracefully(session);
                clear(secretCopy);
                clear(passphraseCopy);
            }
        } finally {
            lifecycleLock.readLock().unlock();
            finishActiveCall(admitted);
        }
    }

    @Override
    public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase,
                                 String script, Duration timeout) {
        return execute(connection, secret, passphrase, script, timeout, ProgressListener.noop());
    }

    @Override
    public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase,
                                 String script, Duration timeout, ProgressListener listener) {
        lifecycleLock.readLock().lock();
        boolean admitted = false;
        try {
            requireOpen();
            admitted = true;
            Objects.requireNonNull(connection, "connection");
            requireSsh2(connection);
            Objects.requireNonNull(script, "script");
            requirePositive(timeout, "timeout");
            Objects.requireNonNull(listener, "listener");
            long started = System.nanoTime();
            char[] secretCopy = copyRequiredSecret(secret);
            char[] passphraseCopy = copyOptional(passphrase);
            ClientSession session = null;
            AtomicBoolean hostKeyMismatch = new AtomicBoolean();
            OperationDeadline connectionDeadline =
                    new OperationDeadline(connection.connectTimeout(), System::nanoTime);
            try {
                session = openSession(client, connection, connectionDeadline, hostKeyMismatch);
                authenticate(session, connection, secretCopy, passphraseCopy, connectionDeadline, hostKeyMismatch);
                return executeChannel(session, connection.executionMode(), script, timeout, started, listener);
            } finally {
                closeGracefully(session);
                clear(secretCopy);
                clear(passphraseCopy);
            }
        } finally {
            lifecycleLock.readLock().unlock();
            finishActiveCall(admitted);
        }
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new ConnectionFailure(ConnectionFailure.Code.CONNECTION_CLOSED,
                    ConnectionFailure.Stage.CONNECT, CONNECTION_CLOSED_MESSAGE);
        }
    }

    private void finishActiveCall(boolean admitted) {
        if (admitted && closed.get() && lifecycleLock.getReadHoldCount() == 0) {
            stopClientWhenIdle();
        }
    }

    static SshClient createClient() {
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((session, remoteAddress, serverKey) -> {
            HostKeyPolicy policy = session instanceof ClientSession clientSession
                    ? clientSession.getConnectionContext().getAttribute(HOST_KEY_POLICY)
                    : null;
            if (policy == null) {
                LOG.log(System.Logger.Level.ERROR,
                        "SSH host key policy is missing from the connection context");
                return false;
            }
            if (policy.expectedFingerprint() == null) {
                LOG.log(System.Logger.Level.WARNING,
                        "SSH host key verification is disabled for this connection");
                return true;
            }
            if (!matchesFingerprint(policy.expectedFingerprint(), serverKey)) {
                policy.mismatch().set(true);
                return false;
            }
            return true;
        });
        client.start();
        return client;
    }

    private record AdapterConfiguration(
            RemoteEndpointPolicy endpointPolicy,
            int maxOutputBytes,
            int eventChunkBytes,
            Pattern commandPromptOverride,
            int maxPages) {
    }

    private ClientSession openSession(SshClient client, ConnectionSpec connection,
                                      OperationDeadline connectionDeadline, AtomicBoolean hostKeyMismatch) {
        ConnectFuture connectFuture = null;
        String connectAddress;
        try {
            connectAddress = endpointPolicy.resolveConnectAddress(connection.host(), connection.port());
        } catch (RuntimeException exception) {
            throw failure(ConnectionFailure.Code.UNREACHABLE, ConnectionFailure.Stage.RESOLVE,
                    UNREACHABLE_MESSAGE, exception);
        }
        try {
            HostKeyPolicy hostKeyPolicy =
                    new HostKeyPolicy(connection.expectedHostKeyFingerprint(), hostKeyMismatch);
            connectFuture = client.connect(
                    connection.username(), connectAddress, connection.port(),
                    AttributeRepository.ofKeyValuePair(HOST_KEY_POLICY, hostKeyPolicy), null);
            return connectFuture.verify(connectionDeadline.remainingOrThrow()).getSession();
        } catch (IOException | RuntimeException exception) {
            if (connectFuture != null) {
                closeImmediately(connectFuture.getSession());
            }
            if (hostKeyMismatch.get()) {
                throw failure(ConnectionFailure.Code.HOST_KEY_MISMATCH, ConnectionFailure.Stage.VERIFY_HOST,
                        HOST_KEY_MISMATCH_MESSAGE, exception);
            }
            if (isTimeout(exception)) {
                throw failure(ConnectionFailure.Code.CONNECT_TIMEOUT, ConnectionFailure.Stage.CONNECT,
                        CONNECT_TIMEOUT_MESSAGE, exception);
            }
            throw failure(ConnectionFailure.Code.UNREACHABLE, ConnectionFailure.Stage.CONNECT,
                    UNREACHABLE_MESSAGE, exception);
        }
    }

    private static void authenticate(ClientSession session, ConnectionSpec connection,
                                     char[] secret, char[] passphrase,
                                     OperationDeadline connectionDeadline, AtomicBoolean hostKeyMismatch) {
        try {
            if (connection.authenticationType() == AuthenticationType.PRIVATE_KEY) {
                addPrivateKeyIdentities(session, secret, passphrase);
            } else {
                session.addPasswordIdentity(new String(secret));
            }
            session.auth().verify(connectionDeadline.remainingOrThrow());
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            if (hostKeyMismatch.get()) {
                throw failure(ConnectionFailure.Code.HOST_KEY_MISMATCH, ConnectionFailure.Stage.VERIFY_HOST,
                        HOST_KEY_MISMATCH_MESSAGE, exception);
            }
            if (isTimeout(exception)) {
                throw failure(ConnectionFailure.Code.CONNECT_TIMEOUT, ConnectionFailure.Stage.CONNECT,
                        CONNECT_TIMEOUT_MESSAGE, exception);
            }
            throw failure(ConnectionFailure.Code.AUTH_FAILED, ConnectionFailure.Stage.AUTHENTICATE,
                    AUTH_FAILED_MESSAGE, exception);
        }
    }

    private static void addPrivateKeyIdentities(ClientSession session, char[] privateKey, char[] passphrase)
            throws IOException, GeneralSecurityException {
        byte[] privateKeyBytes = utf8(privateKey);
        try {
            FilePasswordProvider passwordProvider = passphrase.length == 0
                    ? FilePasswordProvider.EMPTY
                    : (sessionContext, resourceKey, retryIndex) -> new String(passphrase);
            Iterable<KeyPair> identities = SecurityUtils.loadKeyPairIdentities(
                    null,
                    NamedResource.ofName("device-ops-private-key"),
                    new ByteArrayInputStream(privateKeyBytes),
                    passwordProvider);
            boolean loaded = false;
            for (KeyPair identity : identities) {
                session.addPublicKeyIdentity(identity);
                loaded = true;
            }
            if (!loaded) {
                throw new GeneralSecurityException("private key contains no usable identity");
            }
        } finally {
            Arrays.fill(privateKeyBytes, (byte) 0);
        }
    }

    private CommandResult executeChannel(ClientSession session, ExecutionMode executionMode,
                                         String script, Duration timeout, long started,
                                         ProgressListener listener) {
        if (executionMode == ExecutionMode.SHELL) {
            return executeShell(session, script, timeout, started, listener);
        }
        return executeExec(session, script, timeout, started, listener);
    }

    private CommandResult executeExec(ClientSession session, String script, Duration timeout, long started,
                                      ProgressListener listener) {
        CommandPlan commandPlan = CommandPlan.fromScript(script);
        OperationDeadline commandDeadline = new OperationDeadline(timeout, System::nanoTime);
        OutputBudget budget = new OutputBudget(maxOutputBytes);
        CapturedOutput stdout = new CapturedOutput();
        CapturedOutput stderr = new CapturedOutput();
        List<CommandOutputBlock> blocks = new ArrayList<>();
        try {
            for (CommandSpec command : commandPlan.commands()) {
                listener.checkCancellation();
                CapturedOutput commandStdout = new CapturedOutput();
                CapturedOutput commandStderr = new CapturedOutput();
                IncrementalUtf8Decoder stdoutDecoder = new IncrementalUtf8Decoder();
                IncrementalUtf8Decoder stderrDecoder = new IncrementalUtf8Decoder();
                OutputEventQueue outputEvents = new OutputEventQueue(maxOutputBytes, eventChunkBytes);
                ClientChannel channel = session.createExecChannel(command.commandText());
                Instant commandStartedAt = Instant.now();
                try {
                    channel.setOut(outputEvents.stream(OutputStreamType.STDOUT));
                    channel.setErr(outputEvents.stream(OutputStreamType.STDERR));
                    openChannel(channel, commandDeadline);
                    while (!commandDeadline.expired()) {
                        listener.checkCancellation();
                        OutputEvent event = outputEvents.poll(commandDeadline.remaining());
                        if (event != null) {
                            String decoded = decoderFor(event.streamType(), stdoutDecoder, stderrDecoder)
                                    .accept(event.bytes());
                            CapturedOutput commandStream = event.streamType() == OutputStreamType.STDOUT
                                    ? commandStdout : commandStderr;
                            if (emit(decoded, event.streamType(), stdout, stderr, commandStream, 0,
                                    budget, listener, command.commandIndex(), 0)) {
                                closeImmediately(channel);
                                blocks.add(block(command, CommandBlockStatus.SUCCEEDED, commandStdout, commandStderr,
                                        0, true, 0, null, commandStartedAt));
                                addCancelled(commandPlan, blocks);
                                return result(0, stdout, stderr, false, true, started, blocks);
                            }
                        }
                        if (channel.isClosed() && outputEvents.isEmpty()) {
                            break;
                        }
                    }
                    if (commandDeadline.expired()) {
                        String stdoutTail = stdoutDecoder.finish();
                        String stderrTail = stderrDecoder.finish();
                        captureBoth(stdoutTail, OutputStreamType.STDOUT, stdout, commandStdout, budget);
                        captureBoth(stderrTail, OutputStreamType.STDERR, stderr, commandStderr, budget);
                        blocks.add(block(command, CommandBlockStatus.TIMED_OUT, commandStdout, commandStderr,
                                -1, budget.truncated(), 0, "command timed out", commandStartedAt));
                        addCancelled(commandPlan, blocks);
                        return result(-1, stdout, stderr, true, budget.truncated(), started, blocks);
                    }
                    emit(stdoutDecoder.finish(), OutputStreamType.STDOUT, stdout, stderr, commandStdout, 0,
                            budget, listener, command.commandIndex(), 0);
                    emit(stderrDecoder.finish(), OutputStreamType.STDERR, stdout, stderr, commandStderr, 0,
                            budget, listener, command.commandIndex(), 0);
                    Integer remoteExitCode = channel.getExitStatus();
                    if (remoteExitCode == null) {
                        throw failure(ConnectionFailure.Code.CONNECTION_CLOSED, ConnectionFailure.Stage.EXECUTE,
                                CONNECTION_CLOSED_MESSAGE,
                                new IOException("remote channel closed without exit status"));
                    }
                    CommandBlockStatus status = remoteExitCode == 0
                            ? CommandBlockStatus.SUCCEEDED : CommandBlockStatus.FAILED;
                    blocks.add(block(command, status, commandStdout, commandStderr, remoteExitCode,
                            budget.truncated(), 0, null, commandStartedAt));
                } finally {
                    closeImmediately(channel);
                }
            }
            int exitCode = blocks.stream().map(CommandOutputBlock::exitCode)
                    .filter(Objects::nonNull).filter(code -> code != 0).findFirst().orElse(0);
            return result(exitCode, stdout, stderr, false, budget.truncated(), started, blocks);
        } catch (ProgressListenerFailure failure) {
            throw failure.original();
        } catch (ConnectionFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException exception) {
            throw failure(ConnectionFailure.Code.CONNECTION_CLOSED, ConnectionFailure.Stage.EXECUTE,
                    CONNECTION_CLOSED_MESSAGE, exception);
        }
    }

    private CommandResult executeShell(ClientSession session, String script, Duration timeout, long started,
                                       ProgressListener listener) {
        OperationDeadline commandDeadline = new OperationDeadline(timeout, System::nanoTime);
        OutputBudget budget = new OutputBudget(maxOutputBytes);
        CapturedOutput stdout = new CapturedOutput();
        CapturedOutput stderr = new CapturedOutput();
        IncrementalUtf8Decoder stderrDecoder = new IncrementalUtf8Decoder();
        OutputEventQueue outputEvents = new OutputEventQueue(maxOutputBytes, eventChunkBytes);
        CommandPlan commandPlan = CommandPlan.fromScript(script);
        List<CommandSpec> commands = commandPlan.commands();
        List<CommandOutputBlock> blocks = new ArrayList<>();
        ShellProgressState shellState = new ShellProgressState(commandPromptOverride, maxPages);
        TerminalPagerController pager = new TerminalPagerController(PAGER_TERMINAL_SETTLE, maxPages);
        CommandSpec activeCommand = null;
        int commandStdoutStart = 0;
        long commandStdoutBytes = 0;
        int commandStderrStart = 0;
        long commandStderrBytes = 0;
        Instant commandStartedAt = null;
        ClientChannel channel = null;
        try {
            PtyChannelConfiguration pty = new PtyChannelConfiguration();
            pty.setPtyType("vt100");
            ChannelShell shell = session.createShellChannel(pty, java.util.Map.of());
            channel = shell;
            shell.setStreaming(StreamingChannel.Streaming.Async);
            openChannel(shell, commandDeadline);
            IoOutputStream shellWriter = shell.getAsyncIn();
            AtomicReference<IOException> asynchronousReadFailure = new AtomicReference<>();
            startAsyncReader(shell, shell.getAsyncOut(), OutputStreamType.STDOUT,
                    outputEvents, asynchronousReadFailure);
            startAsyncReader(shell, shell.getAsyncErr(), OutputStreamType.STDERR,
                    outputEvents, asynchronousReadFailure);

            boolean inputClosed = false;
            int nextCommand = 0;
            while (!commandDeadline.expired()) {
                listener.checkCancellation();
                IOException readFailure = asynchronousReadFailure.get();
                if (readFailure != null) {
                    throw readFailure;
                }
                OutputEvent event = outputEvents.poll(commandDeadline.remaining());
                    if (event != null && event.streamType() == OutputStreamType.STDOUT) {
                        TerminalTextProcessor.Decision decision =
                                shellState.accept(event.bytes());
                        pager.observe(decision);
                        shellState.pageCount = pager.pageCount();
                        if (shellState.initialPromptReached
                                && activeCommand != null
                                && emit(decision.output(), OutputStreamType.STDOUT, stdout, stderr, null,
                                commandStdoutBytes, budget, listener, activeCommand.commandIndex(),
                                shellState.pageCount)) {
                            blocks.add(block(activeCommand, CommandBlockStatus.SUCCEEDED,
                                    stdout.slice(commandStdoutStart, commandStdoutBytes),
                                    stderr.slice(commandStderrStart, commandStderrBytes), 0,
                                    true, shellState.pageCount, null, commandStartedAt));
                            addCancelled(commandPlan, blocks);
                            closeImmediately(channel);
                            return result(0, stdout, stderr, false, true, started, blocks);
                        }
                        if (decision.promptReached()) {
                            if (!shellState.initialPromptReached) {
                                shellState.initialPromptReached = true;
                            }
                            if (activeCommand != null) {
                                blocks.add(block(activeCommand, CommandBlockStatus.SUCCEEDED,
                                        stdout.slice(commandStdoutStart, commandStdoutBytes),
                                        stderr.slice(commandStderrStart, commandStderrBytes), 0,
                                        budget.truncated(), shellState.pageCount, null, commandStartedAt));
                                activeCommand = null;
                            }
                            if (!inputClosed && nextCommand < commands.size()) {
                                listener.checkCancellation();
                                activeCommand = commands.get(nextCommand++);
                                commandStdoutStart = stdout.length();
                                commandStdoutBytes = stdout.receivedBytes();
                                commandStderrStart = stderr.length();
                                commandStderrBytes = stderr.receivedBytes();
                                commandStartedAt = Instant.now();
                                shellState.pageCount = 0;
                                writeShell(shellWriter, activeCommand.commandText() + "\n", commandDeadline);
                            } else if (!inputClosed) {
                                inputClosed = true;
                                break;
                            }
                        }
                    } else if (event != null && event.streamType() == OutputStreamType.STDERR) {
                        String decoded = stderrDecoder.accept(event.bytes());
                        if (activeCommand != null && emit(decoded, OutputStreamType.STDERR, stdout, stderr, null,
                                commandStderrBytes, budget, listener, activeCommand.commandIndex(),
                                shellState.pageCount)) {
                            blocks.add(block(activeCommand, CommandBlockStatus.SUCCEEDED,
                                    stdout.slice(commandStdoutStart, commandStdoutBytes),
                                    stderr.slice(commandStderrStart, commandStderrBytes), 0,
                                    true, shellState.pageCount, null, commandStartedAt));
                            addCancelled(commandPlan, blocks);
                            closeImmediately(channel);
                            return result(0, stdout, stderr, false, true, started, blocks);
                        }
                    }
                    // Pager implementations may keep repainting cursor-control bytes while waiting. Use a
                    // bounded settle window instead of requiring the output stream to become fully quiet.
                    if (pager.continuationReady()) {
                        writeShellContinuation(shellWriter, commandDeadline);
                        pager.markContinuationSent();
                        shellState.pageCount = pager.pageCount();
                    }
                    if (channel.isClosed() && outputEvents.isEmpty()) {
                        break;
                    }
            }
            if (commandDeadline.expired()) {
                throw new OperationTimeoutException();
            }
            TerminalTextProcessor.Decision finalDecision = shellState.finish();
            if (shellState.initialPromptReached && activeCommand != null) {
                emit(finalDecision.output(), OutputStreamType.STDOUT, stdout, stderr, null,
                        commandStdoutBytes, budget, listener, activeCommand.commandIndex(),
                        finalDecision.pageCount());
            }
            if (activeCommand != null) {
                emit(stderrDecoder.finish(), OutputStreamType.STDERR, stdout, stderr, null,
                        commandStderrBytes, budget, listener, activeCommand.commandIndex(),
                        finalDecision.pageCount());
                blocks.add(block(activeCommand, CommandBlockStatus.SUCCEEDED,
                        stdout.slice(commandStdoutStart, commandStdoutBytes),
                        stderr.slice(commandStderrStart, commandStderrBytes), 0,
                        budget.truncated(), finalDecision.pageCount(), null, commandStartedAt));
            }
            Integer remoteExitCode = channel.getExitStatus();
            if (remoteExitCode == null && !inputClosed) {
                throw failure(ConnectionFailure.Code.CONNECTION_CLOSED, ConnectionFailure.Stage.EXECUTE,
                        CONNECTION_CLOSED_MESSAGE, new IOException("remote channel closed without exit status"));
            }
            int exitCode = remoteExitCode == null ? 0 : remoteExitCode;
            addCancelled(commandPlan, blocks);
            return result(exitCode, stdout, stderr, false, budget.truncated(), started, blocks);
        } catch (ProgressListenerFailure failure) {
            throw failure.original();
        } catch (OperationTimeoutException timeoutFailure) {
            return finishTimedOutShell(
                    channel, outputEvents, stderrDecoder, shellState,
                    stdout, stderr, budget, started);
        } catch (ConnectionFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException exception) {
            throw failure(ConnectionFailure.Code.CONNECTION_CLOSED, ConnectionFailure.Stage.EXECUTE,
                    CONNECTION_CLOSED_MESSAGE, exception);
        } finally {
            closeImmediately(channel);
        }
    }

    private CommandResult finishTimedOutExec(
            ClientChannel channel,
            OutputEventQueue outputEvents,
            IncrementalUtf8Decoder stdoutDecoder,
            IncrementalUtf8Decoder stderrDecoder,
            CapturedOutput stdout,
            CapturedOutput stderr,
            OutputBudget budget,
            long started) {
        outputEvents.stopAccepting();
        closeImmediately(channel);
        List<OutputEvent> queued = outputEvents.drain();
        boolean outputLimitReached = budget.truncated();
        for (OutputEvent event : queued) {
            String decoded = decoderFor(event.streamType(), stdoutDecoder, stderrDecoder)
                    .accept(event.bytes());
            if (!outputLimitReached) {
                outputLimitReached = capture(
                        decoded, event.streamType(), stdout, stderr, budget);
            }
        }
        String stdoutTail = stdoutDecoder.finish();
        String stderrTail = stderrDecoder.finish();
        if (!outputLimitReached) {
            outputLimitReached = capture(
                    stdoutTail, OutputStreamType.STDOUT, stdout, stderr, budget);
        }
        if (!outputLimitReached) {
            capture(stderrTail, OutputStreamType.STDERR, stdout, stderr, budget);
        }
        return timedOut(stdout, stderr, budget.truncated(), started);
    }

    private CommandResult finishTimedOutShell(
            ClientChannel channel,
            OutputEventQueue outputEvents,
            IncrementalUtf8Decoder stderrDecoder,
            ShellProgressState shellState,
            CapturedOutput stdout,
            CapturedOutput stderr,
            OutputBudget budget,
            long started) {
        outputEvents.stopAccepting();
        closeImmediately(channel);
        List<OutputEvent> queued = outputEvents.drain();
        boolean outputLimitReached = budget.truncated();
        for (OutputEvent event : queued) {
            if (event.streamType() == OutputStreamType.STDOUT) {
                TerminalTextProcessor.Decision decision =
                        shellState.accept(event.bytes());
                shellState.pageCount = decision.pageCount();
                if (shellState.initialPromptReached && !outputLimitReached) {
                    outputLimitReached = capture(
                            decision.output(), OutputStreamType.STDOUT, stdout, stderr, budget);
                }
                if (decision.promptReached()) {
                    shellState.initialPromptReached = true;
                }
            } else {
                String decoded = stderrDecoder.accept(event.bytes());
                if (!outputLimitReached) {
                    outputLimitReached = capture(
                            decoded, OutputStreamType.STDERR, stdout, stderr, budget);
                }
            }
        }
        TerminalTextProcessor.Decision finalDecision = shellState.finish();
        String stderrTail = stderrDecoder.finish();
        if (shellState.initialPromptReached && !outputLimitReached) {
            outputLimitReached = capture(
                    finalDecision.output(), OutputStreamType.STDOUT, stdout, stderr, budget);
        }
        if (!outputLimitReached) {
            capture(stderrTail, OutputStreamType.STDERR, stdout, stderr, budget);
        }
        return timedOut(stdout, stderr, budget.truncated(), started);
    }

    static boolean matchesFingerprint(String expectedFingerprint, PublicKey serverKey) {
        return expectedFingerprint != null
                && !expectedFingerprint.isBlank()
                && serverKey != null
                && Boolean.TRUE.equals(KeyUtils.checkFingerPrint(expectedFingerprint.strip(), serverKey).getKey());
    }

    private static List<String> normalizedCommands(String script) {
        String normalized = script.replace("\r\n", "\n").replace('\r', '\n');
        List<String> commands = new ArrayList<>();
        for (String line : normalized.split("\n")) {
            String command = line.strip();
            if (!command.isEmpty()) {
                commands.add(command);
            }
        }
        return List.copyOf(commands);
    }

    private static void writeShell(IoOutputStream output, String value, OperationDeadline deadline) throws IOException {
        output.writeBuffer(new ByteArrayBuffer(value.getBytes(StandardCharsets.UTF_8)))
                .verify(deadline.remainingOrThrow());
    }

    /**
     * Writes terminal flow-control input directly to the remote channel. Control input is deliberately
     * kept outside command snapshots and output progress, so it can never become collection evidence.
     */
    private static void writeShellContinuation(IoOutputStream output, OperationDeadline deadline) throws IOException {
        output.writeBuffer(new ByteArrayBuffer(new byte[]{' '})).verify(deadline.remainingOrThrow());
    }

    private void startAsyncReader(
            ClientChannel channel,
            IoInputStream input,
            OutputStreamType streamType,
            OutputEventQueue outputEvents,
            AtomicReference<IOException> failure) {
        Thread.ofVirtual().name("device-ops-ssh-" + streamType.name().toLowerCase()).start(() -> {
            try {
                while (!channel.isClosed()) {
                    ByteArrayBuffer buffer = new ByteArrayBuffer(eventChunkBytes);
                    var readFuture = input.read(buffer).verify();
                    int read = readFuture.getRead();
                    if (read < 0) {
                        return;
                    }
                    if (read > 0) {
                        byte[] bytes = new byte[read];
                        readFuture.getBuffer().getRawBytes(bytes);
                        outputEvents.accept(streamType, bytes, 0, bytes.length);
                    }
                }
            } catch (IOException exception) {
                if (!channel.isClosing() && !channel.isClosed()) {
                    failure.compareAndSet(null, exception);
                }
            }
        });
    }

    private static void openChannel(ClientChannel channel, OperationDeadline deadline) throws IOException {
        try {
            channel.open().verify(deadline.remainingOrThrow());
        } catch (IOException | RuntimeException exception) {
            if (isTimeout(exception)) {
                throw new OperationTimeoutException(exception);
            }
            throw exception;
        }
    }

    private boolean emit(
            String text,
            OutputStreamType streamType,
            CapturedOutput stdout,
            CapturedOutput stderr,
            CapturedOutput blockTarget,
            long blockByteOffset,
            OutputBudget budget,
            ProgressListener listener,
            int commandIndex,
            int pageCount) {
        CapturedOutput target = streamType == OutputStreamType.STDOUT ? stdout : stderr;
        int offset = 0;
        while (offset < text.length()) {
            int allowedBytes = Math.min(eventChunkBytes, budget.remaining());
            if (allowedBytes == 0) {
                budget.markTruncated();
                notifyProgress(listener, new OutputProgress(
                        commandIndex, streamType, "", blockReceivedBytes(target, blockTarget, blockByteOffset),
                        pageCount, true));
                return true;
            }
            int end = utf8PrefixEnd(text, offset, allowedBytes);
            if (end == offset) {
                budget.markTruncated();
                notifyProgress(listener, new OutputProgress(
                        commandIndex, streamType, "", blockReceivedBytes(target, blockTarget, blockByteOffset),
                        pageCount, true));
                return true;
            }
            String chunk = text.substring(offset, end);
            int chunkBytes = chunk.getBytes(StandardCharsets.UTF_8).length;
            target.append(chunk, chunkBytes);
            if (blockTarget != null) {
                blockTarget.append(chunk, chunkBytes);
            }
            boolean truncated = budget.consume(chunkBytes);
            notifyProgress(listener, new OutputProgress(
                    commandIndex, streamType, chunk,
                    blockReceivedBytes(target, blockTarget, blockByteOffset), pageCount, truncated));
            if (truncated) {
                return true;
            }
            offset = end;
        }
        return false;
    }

    private static long blockReceivedBytes(
            CapturedOutput target,
            CapturedOutput blockTarget,
            long blockByteOffset) {
        return blockTarget == null ? target.receivedBytes() - blockByteOffset : blockTarget.receivedBytes();
    }

    private static void captureBoth(
            String text,
            OutputStreamType streamType,
            CapturedOutput aggregate,
            CapturedOutput block,
            OutputBudget budget) {
        if (text.isEmpty()) {
            return;
        }
        int end = utf8PrefixEnd(text, 0, budget.remaining());
        if (end > 0) {
            String accepted = text.substring(0, end);
            int bytes = accepted.getBytes(StandardCharsets.UTF_8).length;
            aggregate.append(accepted, bytes);
            block.append(accepted, bytes);
            budget.consume(bytes);
        }
        if (end < text.length()) {
            budget.markTruncated();
        }
    }

    private static void notifyProgress(ProgressListener listener, OutputProgress progress) {
        try {
            listener.onProgress(progress);
        } catch (RuntimeException exception) {
            throw new ProgressListenerFailure(exception);
        }
    }

    private static boolean capture(
            String text,
            OutputStreamType streamType,
            CapturedOutput stdout,
            CapturedOutput stderr,
            OutputBudget budget) {
        if (text.isEmpty()) {
            return budget.truncated();
        }
        CapturedOutput target = streamType == OutputStreamType.STDOUT ? stdout : stderr;
        int end = utf8PrefixEnd(text, 0, budget.remaining());
        if (end > 0) {
            String accepted = text.substring(0, end);
            int acceptedBytes = accepted.getBytes(StandardCharsets.UTF_8).length;
            target.append(accepted, acceptedBytes);
            budget.consume(acceptedBytes);
        }
        if (end < text.length()) {
            budget.markTruncated();
        }
        return budget.truncated();
    }

    private static int utf8PrefixEnd(String text, int start, int maxBytes) {
        int index = start;
        int bytes = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int codePointBytes = utf8Length(codePoint);
            if (bytes + codePointBytes > maxBytes) {
                break;
            }
            bytes += codePointBytes;
            index += Character.charCount(codePoint);
        }
        return index;
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }

    private static IncrementalUtf8Decoder decoderFor(
            OutputStreamType streamType,
            IncrementalUtf8Decoder stdout,
            IncrementalUtf8Decoder stderr) {
        return streamType == OutputStreamType.STDOUT ? stdout : stderr;
    }

    private static CommandResult timedOut(
            CapturedOutput stdout,
            CapturedOutput stderr,
            boolean truncated,
            long started) {
        CommandOutputBlock legacy = CommandOutputBlock.legacy(
                stdout.text(), stderr.text(), -1, truncated, java.util.Map.of(), "command timed out");
        return result(-1, stdout, stderr, true, truncated, started, List.of(legacy));
    }

    private static CommandResult result(
            int exitCode,
            CapturedOutput stdout,
            CapturedOutput stderr,
            boolean timedOut,
            boolean truncated,
            long started,
            List<CommandOutputBlock> commandBlocks) {
        return new CommandResult(exitCode, stdout.text(), stderr.text(), timedOut,
                truncated, elapsedMillis(started), commandBlocks);
    }

    private static CommandOutputBlock block(
            CommandSpec command,
            CommandBlockStatus status,
            CapturedOutput stdout,
            CapturedOutput stderr,
            Integer exitCode,
            boolean truncated,
            int pageCount,
            String outcome,
            Instant startedAt) {
        return new CommandOutputBlock(command.commandIndex(), command.commandText(), status,
                stdout.text(), stderr.text(), stdout.receivedBytes() + stderr.receivedBytes(), pageCount,
                truncated, exitCode, outcome, java.util.Map.of(), List.of(), startedAt, Instant.now(), false);
    }

    private static void addCancelled(CommandPlan plan, List<CommandOutputBlock> blocks) {
        for (int index = blocks.size(); index < plan.commands().size(); index++) {
            CommandSpec command = plan.commands().get(index);
            blocks.add(new CommandOutputBlock(command.commandIndex(), command.commandText(),
                    CommandBlockStatus.CANCELLED, "", "", 0, 0, false,
                    null, "not executed", java.util.Map.of(), List.of(), null, Instant.now(), false));
        }
    }

    private static long elapsedNanos(long started) {
        return System.nanoTime() - started;
    }

    private static long elapsedMillis(long started) {
        return Duration.ofNanos(elapsedNanos(started)).toMillis();
    }

    private static char[] copyRequiredSecret(char[] value) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("SSH credential is required");
        }
        return Arrays.copyOf(value, value.length);
    }

    private static char[] copyOptional(char[] value) {
        return value == null ? new char[0] : Arrays.copyOf(value, value.length);
    }

    private static byte[] utf8(char[] value) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        if (encoded.hasArray()) {
            Arrays.fill(encoded.array(), (byte) 0);
        }
        return bytes;
    }

    private static void clear(char[] value) {
        Arrays.fill(value, '\0');
    }

    private static void requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireSsh2(ConnectionSpec connection) {
        if (connection.protocol() != ConnectionProtocol.SSH2) {
            throw new ConnectionFailure(ConnectionFailure.Code.PROTOCOL_DISABLED,
                    ConnectionFailure.Stage.CONNECT, PROTOCOL_DISABLED_MESSAGE);
        }
    }

    private static ConnectionFailure failure(ConnectionFailure.Code code, ConnectionFailure.Stage stage,
                                             String safeMessage, Throwable cause) {
        return new ConnectionFailure(code, stage, safeMessage, cause);
    }

    private static boolean isTimeout(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof OperationTimeoutException
                    || current.getClass().getSimpleName().contains("TimeoutException")) {
                return true;
            }
        }
        return false;
    }

    private static void closeImmediately(ClientChannel channel) {
        if (channel != null) {
            channel.close(true);
        }
    }

    private static void closeImmediately(ClientSession session) {
        if (session != null) {
            session.close(true);
        }
    }

    private static void closeGracefully(ClientSession session) {
        if (session == null) {
            return;
        }
        try {
            if (!session.close(false).await(Duration.ofSeconds(2))) {
                session.close(true).await(Duration.ofSeconds(1));
            }
        } catch (IOException | RuntimeException ignored) {
            try {
                session.close(true).await(Duration.ofSeconds(1));
            } catch (IOException | RuntimeException cleanupFailure) {
                // Cleanup failures must not replace the command result or its original exception.
            }
        }
    }

    @Override
    public void close() {
        closed.set(true);
        if (lifecycleLock.getReadHoldCount() > 0) {
            return;
        }
        stopClientWhenIdle();
    }

    /** Stops the shared client without waiting for active read locks after callers have been cancelled. */
    public void forceClose() {
        closed.set(true);
        stopClientOnce(true);
    }

    private void stopClientWhenIdle() {
        if (clientStopped.get()) {
            return;
        }
        lifecycleLock.writeLock().lock();
        try {
            stopClientOnce(false);
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private void stopClientOnce(boolean force) {
        if (clientStopped.compareAndSet(false, true)) {
            (force ? clientForceStopper : clientStopper).accept(client);
        }
    }

    private record HostKeyPolicy(String expectedFingerprint, AtomicBoolean mismatch) {
        private HostKeyPolicy {
            Objects.requireNonNull(mismatch, "mismatch");
        }
    }

    static final class OperationDeadline {

        private final long deadlineNanos;
        private final LongSupplier nanoTime;

        OperationDeadline(Duration timeout, LongSupplier nanoTime) {
            requirePositive(timeout, "timeout");
            this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
            this.deadlineNanos = nanoTime.getAsLong() + timeout.toNanos();
        }

        Duration remaining() {
            long remainingNanos = deadlineNanos - nanoTime.getAsLong();
            return remainingNanos <= 0 ? Duration.ZERO : Duration.ofNanos(remainingNanos);
        }

        Duration remainingOrThrow() {
            Duration remaining = remaining();
            if (remaining.isZero()) {
                throw new OperationTimeoutException();
            }
            return remaining;
        }

        boolean expired() {
            return remaining().isZero();
        }
    }

    private static final class OperationTimeoutException extends RuntimeException {

        private OperationTimeoutException() {
        }

        private OperationTimeoutException(Throwable cause) {
            super(cause);
        }
    }

    private static final class ProgressListenerFailure extends RuntimeException {

        private final RuntimeException original;

        private ProgressListenerFailure(RuntimeException original) {
            super(original);
            this.original = original;
        }

        private RuntimeException original() {
            return original;
        }
    }

    private static final class ShellProgressState {

        private final Pattern commandPromptOverride;
        private final int maxPages;
        private final TerminalTextProcessor initialTerminal;

        private TerminalTextProcessor commandTerminal;
        private boolean initialPromptReached;
        private int pageCount;

        private ShellProgressState(Pattern commandPromptOverride, int maxPages) {
            this.commandPromptOverride = commandPromptOverride;
            this.maxPages = maxPages;
            this.initialTerminal = new TerminalTextProcessor(INITIAL_PROMPT_CANDIDATE, maxPages);
        }

        private TerminalTextProcessor.Decision accept(byte[] bytes) {
            if (commandTerminal != null) {
                return update(commandTerminal.accept(bytes, bytes.length));
            }
            return captureInitialPrompt(initialTerminal.accept(bytes, bytes.length));
        }

        private TerminalTextProcessor.Decision finish() {
            if (commandTerminal != null) {
                return update(commandTerminal.finish());
            }
            return captureInitialPrompt(initialTerminal.finish());
        }

        private TerminalTextProcessor.Decision captureInitialPrompt(TerminalTextProcessor.Decision decision) {
            pageCount = decision.pageCount();
            if (decision.promptReached()) {
                ObservedPromptIdentity identity =
                        ObservedPromptIdentity.capture(decision.output(), commandPromptOverride);
                commandTerminal = new TerminalTextProcessor(identity::matches, maxPages);
                initialPromptReached = true;
                return new TerminalTextProcessor.Decision("", decision.sendContinue(), true, pageCount);
            }
            return new TerminalTextProcessor.Decision("", decision.sendContinue(), false, pageCount);
        }

        private TerminalTextProcessor.Decision update(TerminalTextProcessor.Decision decision) {
            pageCount = decision.pageCount();
            return decision;
        }

    }

    private record ObservedPromptIdentity(String prompt, Pattern override) {

        private static ObservedPromptIdentity capture(String initialOutput, Pattern override) {
            String prompt = terminalTail(initialOutput);
            if (!hasPromptTerminator(prompt)) {
                throw new IllegalArgumentException("initial SSH prompt is invalid");
            }
            String body = prompt.substring(0, prompt.length() - 1).stripTrailing();
            if (body.isBlank()) {
                throw new IllegalArgumentException("initial SSH prompt identity is empty");
            }
            if (override != null && !patternMatchesAtEnd(override, initialOutput)) {
                throw new IllegalArgumentException("initial SSH prompt does not match the configured override");
            }
            return new ObservedPromptIdentity(prompt, override);
        }

        private boolean matches(CharSequence terminalText) {
            String prompt = terminalTail(terminalText);
            if (!this.prompt.equals(prompt)
                    || override != null && !patternMatchesAtEnd(override, terminalText)) {
                return false;
            }
            return true;
        }

        private static String terminalTail(CharSequence terminalText) {
            int end = terminalText.length();
            while (end > 0 && Character.isWhitespace(terminalText.charAt(end - 1))) {
                end--;
            }
            int start = end;
            while (start > 0) {
                char previous = terminalText.charAt(start - 1);
                if (previous == '\n' || previous == '\r') {
                    break;
                }
                start--;
            }
            return terminalText.subSequence(start, end).toString();
        }

        private static boolean hasPromptTerminator(String prompt) {
            if (prompt.isEmpty()) {
                return false;
            }
            char terminator = prompt.charAt(prompt.length() - 1);
            return terminator == '>' || terminator == '#' || terminator == '$';
        }

        private static boolean patternMatchesAtEnd(Pattern pattern, CharSequence terminalText) {
            int terminalEnd = terminalText.length();
            while (terminalEnd > 0 && Character.isWhitespace(terminalText.charAt(terminalEnd - 1))) {
                terminalEnd--;
            }
            var matcher = pattern.matcher(terminalText.subSequence(0, terminalEnd));
            boolean reached = false;
            while (matcher.find()) {
                reached = matcher.end() == terminalEnd;
            }
            return reached;
        }
    }

    private static final class CapturedOutput {

        private final StringBuilder text = new StringBuilder();
        private long receivedBytes;

        private void append(String value, int byteLength) {
            text.append(value);
            receivedBytes += byteLength;
        }

        private long receivedBytes() {
            return receivedBytes;
        }

        private String text() {
            return text.toString();
        }

        private int length() {
            return text.length();
        }

        private CapturedOutput slice(int characterOffset, long byteOffset) {
            CapturedOutput slice = new CapturedOutput();
            String value = text.substring(characterOffset);
            slice.append(value, Math.toIntExact(receivedBytes - byteOffset));
            return slice;
        }
    }

    private static final class OutputBudget {

        private int remaining;
        private boolean truncated;

        private OutputBudget(int limit) {
            remaining = limit;
        }

        private synchronized int remaining() {
            return remaining;
        }

        private synchronized boolean consume(int bytes) {
            if (bytes < 0 || bytes > remaining) {
                throw new IllegalArgumentException("output bytes exceed remaining budget");
            }
            remaining -= bytes;
            truncated |= remaining == 0;
            return truncated;
        }

        private synchronized void markTruncated() {
            truncated = true;
        }

        private synchronized boolean truncated() {
            return truncated;
        }
    }

    private static final class IncrementalUtf8Decoder {

        private final java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        private ByteBuffer undecoded = emptyByteBuffer();

        private String accept(byte[] bytes) {
            return decode(bytes, false);
        }

        private String finish() {
            return decode(new byte[0], true);
        }

        private String decode(byte[] bytes, boolean endOfInput) {
            ByteBuffer input = ByteBuffer.allocate(undecoded.remaining() + bytes.length);
            input.put(undecoded);
            input.put(bytes);
            input.flip();
            CharBuffer characters = CharBuffer.allocate(Math.max(8, input.remaining() + 1));
            try {
                CoderResult decodeResult = decoder.decode(input, characters, endOfInput);
                if (decodeResult.isError()) {
                    decodeResult.throwException();
                }
                if (endOfInput) {
                    CoderResult flushResult = decoder.flush(characters);
                    if (flushResult.isError()) {
                        flushResult.throwException();
                    }
                }
            } catch (CharacterCodingException exception) {
                throw new IllegalStateException("SSH output is not valid UTF-8", exception);
            }
            ByteBuffer remainder = ByteBuffer.allocate(Math.max(4, input.remaining()));
            remainder.put(input);
            remainder.flip();
            undecoded = remainder;
            characters.flip();
            return characters.toString();
        }

        private static ByteBuffer emptyByteBuffer() {
            ByteBuffer empty = ByteBuffer.allocate(0);
            empty.flip();
            return empty;
        }
    }

    private static final class OutputEventQueue {

        private final BlockingQueue<OutputEvent> events;
        private final int eventChunkBytes;
        private final Object producerLock = new Object();
        private boolean accepting = true;

        private OutputEventQueue(int maxOutputBytes, int eventChunkBytes) {
            int capacity = Math.max(16, Math.min(2_048, maxOutputBytes / eventChunkBytes + 4));
            this.events = new ArrayBlockingQueue<>(capacity);
            this.eventChunkBytes = eventChunkBytes;
        }

        private OutputStream stream(OutputStreamType streamType) {
            return new OutputStream() {
                @Override
                public void write(int value) throws IOException {
                    write(new byte[]{(byte) value});
                }

                @Override
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    accept(streamType, bytes, offset, length);
                }
            };
        }

        private void accept(OutputStreamType streamType, byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            synchronized (producerLock) {
                if (!accepting) {
                    throw new IOException("SSH output collection is closed");
                }
                int position = offset;
                int remaining = length;
                while (remaining > 0) {
                    int chunkLength = Math.min(remaining, eventChunkBytes);
                    byte[] chunk = Arrays.copyOfRange(bytes, position, position + chunkLength);
                    if (!events.offer(new OutputEvent(streamType, chunk))) {
                        throw new IOException("SSH output buffer is full");
                    }
                    position += chunkLength;
                    remaining -= chunkLength;
                }
            }
        }

        private OutputEvent poll(Duration remaining) throws IOException {
            long waitMillis = Math.max(1, Math.min(100, remaining.toMillis()));
            try {
                return events.poll(waitMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while reading SSH output", exception);
            }
        }

        private boolean isEmpty() {
            return events.isEmpty();
        }

        private void stopAccepting() {
            synchronized (producerLock) {
                accepting = false;
            }
        }

        private List<OutputEvent> drain() {
            List<OutputEvent> queued = new ArrayList<>(events.size());
            events.drainTo(queued);
            return queued;
        }
    }

    private record OutputEvent(OutputStreamType streamType, byte[] bytes) {
    }
}
