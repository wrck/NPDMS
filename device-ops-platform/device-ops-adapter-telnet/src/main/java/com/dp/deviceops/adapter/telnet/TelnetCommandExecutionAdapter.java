package com.dp.deviceops.adapter.telnet;

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
import org.apache.commons.net.telnet.TelnetClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TelnetCommandExecutionAdapter implements ProtocolCommandExecutionAdapter {

    private static final int COMMAND_READ_SLICE_MILLIS = 100;
    private static final Duration PAGER_TERMINAL_SETTLE = Duration.ofMillis(250);
    private static final Pattern ANSI_SEQUENCE = Pattern.compile(
            "\\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\\u0007]*(?:\\u0007|\\u001B\\\\))");

    public static final int DEFAULT_MAX_OUTPUT_BYTES = 8 * 1024 * 1024;
    public static final int DEFAULT_EVENT_CHUNK_BYTES = 8_192;

    private static final System.Logger LOG = System.getLogger(TelnetCommandExecutionAdapter.class.getName());
    private static final int PROMPT_WINDOW_BYTES = 8_192;
    private static final String PROTOCOL_DISABLED_MESSAGE = "connection protocol is disabled";
    private static final String UNREACHABLE_MESSAGE = "remote endpoint is unreachable";
    private static final String CONNECT_TIMEOUT_MESSAGE = "connection timed out";
    private static final String PROMPT_NOT_FOUND_MESSAGE = "expected Telnet prompt was not found";
    private static final String AUTH_FAILED_MESSAGE = "authentication failed";
    private static final String CONNECTION_CLOSED_MESSAGE = "connection closed during command execution";

    private final RemoteEndpointPolicy endpointPolicy;
    private final boolean enabled;
    private final int maxOutputBytes;
    private final int eventChunkBytes;
    private final int maxPages;

    public TelnetCommandExecutionAdapter(RemoteEndpointPolicy endpointPolicy, boolean enabled, int maxOutputBytes) {
        this(endpointPolicy, enabled, maxOutputBytes, DEFAULT_EVENT_CHUNK_BYTES,
                TerminalTextProcessor.DEFAULT_MAX_PAGES);
    }

    public TelnetCommandExecutionAdapter(
            RemoteEndpointPolicy endpointPolicy,
            boolean enabled,
            int maxOutputBytes,
            int eventChunkBytes,
            int maxPages) {
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "endpointPolicy");
        if (maxOutputBytes < 1) {
            throw new IllegalArgumentException("maxOutputBytes must be positive");
        }
        if (eventChunkBytes < 1 || eventChunkBytes > DEFAULT_EVENT_CHUNK_BYTES) {
            throw new IllegalArgumentException("eventChunkBytes must be between 1 and 8192");
        }
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must be positive");
        }
        this.enabled = enabled;
        this.maxOutputBytes = maxOutputBytes;
        this.eventChunkBytes = eventChunkBytes;
        this.maxPages = maxPages;
    }

    @Override
    public ConnectionProtocol protocol() {
        return ConnectionProtocol.TELNET;
    }

    @Override
    public void test(ConnectionSpec connection, char[] secret, char[] passphrase) {
        requireEnabled();
        requireTelnet(connection, passphrase);
        char[] password = copyRequiredSecret(secret);
        try (Session ignored = login(connection, password)) {
            // A command prompt proves that authentication completed.
        } finally {
            clear(password);
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
        requireEnabled();
        requireTelnet(connection, passphrase);
        Objects.requireNonNull(script, "script");
        requirePositive(timeout, "timeout");
        Objects.requireNonNull(listener, "listener");
        long started = System.nanoTime();
        char[] password = copyRequiredSecret(secret);
        OutputAccumulator output = new OutputAccumulator(maxOutputBytes, eventChunkBytes);
        ExecutionState execution = null;
        try (Session session = login(connection, password)) {
            Deadline deadline = new Deadline(timeout);
            execution = new ExecutionState(session.commandPrompt(), maxPages);
            TerminalPagerController pager = new TerminalPagerController(PAGER_TERMINAL_SETTLE, maxPages);
            byte[] buffer = new byte[eventChunkBytes];
            CommandPlan commandPlan = CommandPlan.fromScript(script);
            output.initialize(commandPlan);
            for (CommandSpec command : commandPlan.commands()) {
                listener.checkCancellation();
                execution.startCommand(command);
                output.startCommand(command);
                session.writeLine(command.commandText());
                while (true) {
                    listener.checkCancellation();
                    int read = readCommand(session.client(), session.input(), buffer, deadline);
                    if (read == 0) {
                        if (pager.continuationReady()) {
                            session.writeContinue();
                            pager.markContinuationSent();
                            execution.updatePageCount(pager.pageCount());
                        }
                        continue;
                    }
                    if (read < 0) {
                        finish(execution, output, listener, true);
                        throw failure(ConnectionFailure.Code.CONNECTION_CLOSED, ConnectionFailure.Stage.EXECUTE,
                                CONNECTION_CLOSED_MESSAGE, new IOException("remote Telnet session closed"));
                    }
                    TerminalTextProcessor.Decision decision = execution.accept(buffer, read);
                    pager.observe(decision);
                    execution.updatePageCount(pager.pageCount());
                    if (output.emit(execution.filter(decision.output()), listener, execution.pageCount())) {
                        return result(0, output, false, started);
                    }
                    if (decision.promptReached()) {
                        if (output.emit(execution.finishCommand(), listener, execution.pageCount())) {
                            return result(0, output, false, started);
                        }
                        output.finishCommand(CommandBlockStatus.SUCCEEDED, 0, null, execution.pageCount());
                        break;
                    }
                }
            }
            finish(execution, output, listener, true);
            return result(0, output, false, started);
        } catch (ProgressListenerFailure failure) {
            throw failure.original();
        } catch (DeadlineExpired | InterruptedIOException timeoutFailure) {
            finish(execution, output, listener, false);
            output.finishCommand(CommandBlockStatus.TIMED_OUT, -1, "command timed out",
                    execution == null ? 0 : execution.pageCount());
            return result(-1, output, true, started);
        } catch (ConnectionFailure failure) {
            finish(execution, output, listener, false);
            throw failure;
        } catch (IOException exception) {
            try {
                finish(execution, output, listener, true);
            } catch (ProgressListenerFailure failure) {
                throw failure.original();
            }
            throw failure(ConnectionFailure.Code.CONNECTION_CLOSED, ConnectionFailure.Stage.EXECUTE,
                    CONNECTION_CLOSED_MESSAGE, exception);
        } finally {
            clear(password);
        }
    }

    private Session login(ConnectionSpec connection, char[] password) {
        RawTelnetClient client = new RawTelnetClient();
        client.setReaderThread(false);
        Deadline deadline = new Deadline(connection.connectTimeout());
        String connectAddress;
        try {
            connectAddress = endpointPolicy.resolveConnectAddress(connection.host(), connection.port());
        } catch (RuntimeException exception) {
            throw failure(ConnectionFailure.Code.UNREACHABLE, ConnectionFailure.Stage.RESOLVE,
                    UNREACHABLE_MESSAGE, exception);
        }
        try {
            client.setConnectTimeout(deadline.remainingMillis());
            client.connect(connectAddress, connection.port());
            InputStream input = client.getInputStream();
            OutputStream output = client.rawOutputStream();
            Pattern loginPrompt = Pattern.compile(connection.telnetPrompts().login());
            Pattern passwordPrompt = Pattern.compile(connection.telnetPrompts().password());
            Pattern commandPrompt = Pattern.compile(connection.telnetPrompts().command());
            TelnetLineEnding lineEnding = resolveLineEnding(connection.telnetPrompts().lineEnding());
            readLoginPrompt(client, input, loginPrompt, deadline, "username");
            writeLine(output, connection.username(), lineEnding);
            readLoginPrompt(client, input, passwordPrompt, deadline, "password");
            writeLine(output, password, lineEnding);
            try {
                readUntilPrompt(client, input, commandPrompt, deadline, maxOutputBytes,
                        loginPrompt, passwordPrompt);
            } catch (PromptRejected exception) {
                logAuthenticationRejected(exception.partial(), loginPrompt, passwordPrompt);
                throw failure(ConnectionFailure.Code.AUTH_FAILED, ConnectionFailure.Stage.AUTHENTICATE,
                        AUTH_FAILED_MESSAGE, exception);
            } catch (PromptClosed exception) {
                throw failure(ConnectionFailure.Code.AUTH_FAILED, ConnectionFailure.Stage.AUTHENTICATE,
                        AUTH_FAILED_MESSAGE, exception);
            } catch (PromptTimeout exception) {
                logPromptFailure("command", exception.partial());
                throw failure(ConnectionFailure.Code.PROMPT_NOT_FOUND, ConnectionFailure.Stage.LOGIN,
                        PROMPT_NOT_FOUND_MESSAGE, exception);
            }
            return new Session(client, input, output, commandPrompt, lineEnding);
        } catch (ConnectionFailure failure) {
            disconnect(client);
            throw failure;
        } catch (SocketTimeoutException exception) {
            disconnect(client);
            throw failure(ConnectionFailure.Code.CONNECT_TIMEOUT, ConnectionFailure.Stage.CONNECT,
                    CONNECT_TIMEOUT_MESSAGE, exception);
        } catch (IOException | RuntimeException exception) {
            disconnect(client);
            if (deadline.expired() || isTimeout(exception)) {
                throw failure(ConnectionFailure.Code.CONNECT_TIMEOUT, ConnectionFailure.Stage.CONNECT,
                        CONNECT_TIMEOUT_MESSAGE, exception);
            }
            throw failure(ConnectionFailure.Code.UNREACHABLE, ConnectionFailure.Stage.CONNECT,
                    UNREACHABLE_MESSAGE, exception);
        }
    }

    private static void readLoginPrompt(TelnetClient client, InputStream input, Pattern prompt, Deadline deadline,
                                        String stage) {
        try {
            readUntilPrompt(client, input, prompt, deadline, 1);
        } catch (PromptTimeout exception) {
            logPromptFailure(stage, exception.partial());
            throw failure(ConnectionFailure.Code.PROMPT_NOT_FOUND, ConnectionFailure.Stage.LOGIN,
                    PROMPT_NOT_FOUND_MESSAGE, exception);
        } catch (PromptClosed exception) {
            logPromptFailure(stage, exception.partial());
            throw failure(ConnectionFailure.Code.PROMPT_NOT_FOUND, ConnectionFailure.Stage.LOGIN,
                    PROMPT_NOT_FOUND_MESSAGE, exception);
        } catch (IOException exception) {
            throw failure(ConnectionFailure.Code.CONNECTION_CLOSED, ConnectionFailure.Stage.LOGIN,
                    CONNECTION_CLOSED_MESSAGE, exception);
        }
    }

    private static void logPromptFailure(String stage, PromptRead partial) {
        String text = new String(partial.content(), StandardCharsets.ISO_8859_1).toLowerCase();
        LOG.log(System.Logger.Level.WARNING,
                "Telnet {0} prompt not found after reading {1} captured byte(s), truncated={2}, "
                        + "usernamePrompt={3}, passwordPrompt={4}, commandMarker={5}, failureMarker={6}",
                stage, partial.content().length, partial.truncated(),
                text.contains("username"), text.contains("password"),
                text.indexOf('>') >= 0 || text.indexOf('#') >= 0,
                text.contains("fail") || text.contains("invalid") || text.contains("error"));
    }

    private static void logAuthenticationRejected(PromptRead partial, Pattern loginPrompt, Pattern passwordPrompt) {
        String response = new String(partial.content(), StandardCharsets.ISO_8859_1);
        LOG.log(System.Logger.Level.WARNING,
                "Telnet authentication rejected because the login or password prompt was shown again "
                        + "after reading {0} captured byte(s), truncated={1}, loginPrompt={2}, passwordPrompt={3}",
                partial.content().length, partial.truncated(),
                matchesAtEnd(loginPrompt, response), matchesAtEnd(passwordPrompt, response));
    }

    private static PromptRead readUntilPrompt(TelnetClient client, InputStream input, Pattern prompt,
                                              Deadline deadline, int captureLimit,
                                              Pattern... rejectedPrompts) throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream(Math.min(captureLimit, 8_192));
        StringBuilder window = new StringBuilder(PROMPT_WINDOW_BYTES);
        long totalBytes = 0;
        while (true) {
            try {
                int value = read(client, input, deadline);
                if (value < 0) {
                    throw new PromptClosed(partial(captured, totalBytes, captureLimit));
                }
                totalBytes++;
                if (captured.size() < captureLimit) {
                    captured.write(value);
                }
                if (window.length() == PROMPT_WINDOW_BYTES) {
                    window.deleteCharAt(0);
                }
                window.append((char) (value & 0xff));
            } catch (DeadlineExpired | InterruptedIOException exception) {
                throw new PromptTimeout(partial(captured, totalBytes, captureLimit));
            }
            String normalizedWindow = normalizePromptText(window);
            Matcher matcher = prompt.matcher(normalizedWindow);
            int promptStart = -1;
            while (matcher.find()) {
                if (matcher.end() == normalizedWindow.length()) {
                    promptStart = matcher.start();
                }
            }
            if (promptStart >= 0) {
                byte[] bytes = captured.toByteArray();
                return new PromptRead(bytes, totalBytes > captureLimit);
            }
            for (Pattern rejectedPrompt : rejectedPrompts) {
                if (matchesAtEnd(rejectedPrompt, normalizedWindow)) {
                    throw new PromptRejected(partial(captured, totalBytes, captureLimit));
                }
            }
        }
    }

    private static boolean matchesAtEnd(Pattern pattern, CharSequence value) {
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            if (matcher.end() == value.length()) {
                return true;
            }
        }
        return false;
    }

    private static String normalizePromptText(CharSequence value) {
        String withoutAnsi = ANSI_SEQUENCE.matcher(value).replaceAll("");
        StringBuilder normalized = new StringBuilder(withoutAnsi.length());
        for (int index = 0; index < withoutAnsi.length(); index++) {
            char character = withoutAnsi.charAt(index);
            if (character == '\0') {
                continue;
            }
            if (character == '\b') {
                if (!normalized.isEmpty()) normalized.deleteCharAt(normalized.length() - 1);
                continue;
            }
            normalized.append(character);
        }
        return normalized.toString();
    }

    private static int read(TelnetClient client, InputStream input, byte[] buffer, Deadline deadline)
            throws IOException {
        client.setSoTimeout(deadline.remainingMillis());
        int read = input.read(buffer);
        if (read >= 0) {
            return read;
        }
        int value = readAfterNegative(client, input, deadline);
        if (value >= 0) {
            buffer[0] = (byte) value;
            return 1;
        }
        return -1;
    }

    private static int readCommand(TelnetClient client, InputStream input, byte[] buffer, Deadline deadline)
            throws IOException {
        client.setSoTimeout(Math.min(COMMAND_READ_SLICE_MILLIS, deadline.remainingMillis()));
        try {
            int read = input.read(buffer);
            if (read >= 0) {
                return read;
            }
            int value = input.read();
            if (value >= 0) {
                buffer[0] = (byte) value;
                return 1;
            }
            return -1;
        } catch (InterruptedIOException timeout) {
            if (deadline.expired()) {
                throw timeout;
            }
            return 0;
        }
    }

    private static int read(TelnetClient client, InputStream input, Deadline deadline) throws IOException {
        client.setSoTimeout(deadline.remainingMillis());
        int value = input.read();
        return value >= 0 ? value : readAfterNegative(client, input, deadline);
    }

    /*
     * In non-threaded mode TelnetInputStream reports a socket timeout as -1
     * once and rethrows its cached InterruptedIOException on the next read.
     * Probe once so this sentinel is not mistaken for a real remote EOF.
     */
    private static int readAfterNegative(TelnetClient client, InputStream input, Deadline deadline)
            throws IOException {
        if (deadline.expired()) {
            throw new SocketTimeoutException("Telnet read timed out");
        }
        client.setSoTimeout(deadline.remainingMillis());
        return input.read();
    }

    private static PromptRead partial(ByteArrayOutputStream captured, long totalBytes, int captureLimit) {
        return new PromptRead(captured.toByteArray(), totalBytes > captureLimit);
    }

    private static void finish(ExecutionState execution, OutputAccumulator output,
                               ProgressListener listener, boolean notify) {
        if (execution == null || execution.finished()) {
            return;
        }
        String tail = execution.finish();
        if (notify) {
            output.emit(tail, listener, execution.pageCount());
        } else {
            output.capture(tail);
        }
    }

    private static CommandResult result(int exitCode, OutputAccumulator output, boolean timedOut, long started) {
        output.cancelRemaining();
        return new CommandResult(exitCode, output.text(), "", timedOut, output.truncated(), elapsedMillis(started),
                output.blocks());
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ConnectionFailure(ConnectionFailure.Code.PROTOCOL_DISABLED,
                    ConnectionFailure.Stage.CONNECT, PROTOCOL_DISABLED_MESSAGE);
        }
    }

    private static void requireTelnet(ConnectionSpec connection, char[] passphrase) {
        Objects.requireNonNull(connection, "connection");
        if (connection.protocol() != ConnectionProtocol.TELNET) {
            throw new ConnectionFailure(ConnectionFailure.Code.PROTOCOL_DISABLED,
                    ConnectionFailure.Stage.CONNECT, PROTOCOL_DISABLED_MESSAGE);
        }
        if (connection.authenticationType() != AuthenticationType.PASSWORD) {
            throw new IllegalArgumentException("TELNET requires password authentication");
        }
        if (connection.executionMode() != ExecutionMode.SHELL) {
            throw new IllegalArgumentException("TELNET requires shell execution mode");
        }
        if (passphrase != null && passphrase.length > 0) {
            throw new IllegalArgumentException("TELNET does not accept a passphrase");
        }
    }

    private static char[] copyRequiredSecret(char[] secret) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("TELNET password is required");
        }
        return Arrays.copyOf(secret, secret.length);
    }

    private static TelnetLineEnding resolveLineEnding(TelnetLineEnding configured) {
        // AUTO models a terminal Enter key. Explicit CRLF remains available for strict NVT peers.
        return configured == null || configured == TelnetLineEnding.AUTO ? TelnetLineEnding.CR : configured;
    }

    private static void writeLine(OutputStream output, String value, TelnetLineEnding lineEnding) throws IOException {
        writeTelnetData(output, value.getBytes(StandardCharsets.UTF_8));
        writeLineEnding(output, lineEnding);
        output.flush();
    }

    private static void writeLine(OutputStream output, char[] value, TelnetLineEnding lineEnding) throws IOException {
        byte[] encoded = new String(value).getBytes(StandardCharsets.UTF_8);
        try {
            writeTelnetData(output, encoded);
            writeLineEnding(output, lineEnding);
            output.flush();
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private static void writeTelnetData(OutputStream output, byte[] value) throws IOException {
        for (byte current : value) {
            int unsigned = current & 0xff;
            output.write(unsigned);
            if (unsigned == 0xff) {
                output.write(unsigned);
            }
        }
    }

    private static void writeLineEnding(OutputStream output, TelnetLineEnding lineEnding) throws IOException {
        switch (lineEnding) {
            case CRLF -> {
                output.write('\r');
                output.write('\n');
            }
            case CR -> output.write('\r');
            case LF -> output.write('\n');
            case AUTO -> throw new IllegalArgumentException("AUTO line ending must be resolved before writing");
        }
    }

    private static void requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private static boolean isTimeout(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof DeadlineExpired) {
                return true;
            }
        }
        return false;
    }

    private static ConnectionFailure failure(ConnectionFailure.Code code, ConnectionFailure.Stage stage,
                                             String safeMessage, Throwable cause) {
        return new ConnectionFailure(code, stage, safeMessage, cause);
    }

    private static void clear(char[] value) {
        Arrays.fill(value, '\0');
    }

    private static void disconnect(TelnetClient client) {
        if (client.isConnected()) {
            try {
                client.disconnect();
            } catch (IOException ignored) {
                // Best-effort cleanup.
            }
        }
    }

    private static int utf8PrefixEnd(String text, int start, int maxBytes) {
        int index = start;
        int bytes = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int codePointBytes;
            if (codePoint <= 0x7F) {
                codePointBytes = 1;
            } else if (codePoint <= 0x7FF) {
                codePointBytes = 2;
            } else if (codePoint <= 0xFFFF) {
                codePointBytes = 3;
            } else {
                codePointBytes = 4;
            }
            if (bytes + codePointBytes > maxBytes) {
                break;
            }
            bytes += codePointBytes;
            index += Character.charCount(codePoint);
        }
        return index;
    }

    private record PromptRead(byte[] content, boolean truncated) {
    }

    private static final class PromptTimeout extends IOException {
        private final PromptRead partial;

        private PromptTimeout(PromptRead partial) {
            this.partial = partial;
        }

        private PromptRead partial() {
            return partial;
        }
    }

    private static final class PromptClosed extends IOException {
        private final PromptRead partial;

        private PromptClosed(PromptRead partial) {
            this.partial = partial;
        }

        private PromptRead partial() {
            return partial;
        }
    }

    private static final class PromptRejected extends IOException {
        private final PromptRead partial;

        private PromptRejected(PromptRead partial) {
            this.partial = partial;
        }

        private PromptRead partial() {
            return partial;
        }
    }

    private static final class DeadlineExpired extends RuntimeException {
    }

    private static final class Deadline {
        private final long deadlineNanos;

        private Deadline(Duration timeout) {
            requirePositive(timeout, "timeout");
            this.deadlineNanos = System.nanoTime() + timeout.toNanos();
        }

        private int remainingMillis() {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException("Telnet execution interrupted");
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new DeadlineExpired();
            }
            long millis = Math.max(1, Duration.ofNanos(remainingNanos).toMillis());
            return (int) Math.min(Integer.MAX_VALUE, millis);
        }

        private boolean expired() {
            return deadlineNanos - System.nanoTime() <= 0;
        }
    }

    private static final class ExecutionState {
        private final TerminalTextProcessor terminal;
        private EchoStripper echo;
        private boolean finished;
        private int pageCount;

        private ExecutionState(Pattern commandPrompt, int maxPages) {
            terminal = new TerminalTextProcessor(commandPrompt, maxPages);
        }

        private void startCommand(CommandSpec command) {
            echo = new EchoStripper(command.commandText());
            pageCount = 0;
        }

        private TerminalTextProcessor.Decision accept(byte[] bytes, int length) {
            TerminalTextProcessor.Decision decision = terminal.accept(bytes, length);
            pageCount = decision.pageCount();
            return decision;
        }

        private String filter(String text) {
            return echo == null ? text : echo.accept(text);
        }

        private String finishCommand() {
            if (echo == null) {
                return "";
            }
            String tail = echo.finish();
            echo = null;
            return tail;
        }

        private String finish() {
            if (finished) {
                return "";
            }
            finished = true;
            TerminalTextProcessor.Decision decision = terminal.finish();
            pageCount = Math.max(pageCount, decision.pageCount());
            return filter(decision.output()) + finishCommand();
        }

        private boolean finished() {
            return finished;
        }

        private int pageCount() {
            return pageCount;
        }

        private void updatePageCount(int observedPageCount) {
            pageCount = Math.max(pageCount, observedPageCount);
        }
    }

    private static final class EchoStripper {
        private final String command;
        private final StringBuilder pending = new StringBuilder();
        private int matchedCharacters;
        private EchoState state = EchoState.MATCHING_COMMAND;

        private EchoStripper(String command) {
            this.command = command.strip();
        }

        private String accept(String text) {
            if (state == EchoState.RESOLVED || text.isEmpty()) {
                return text;
            }
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                pending.append(character);
                if (state == EchoState.MATCHING_COMMAND) {
                    if (character != command.charAt(matchedCharacters)) {
                        return release(text, index + 1);
                    }
                    matchedCharacters++;
                    if (matchedCharacters == command.length()) {
                        state = EchoState.EXPECTING_LINE_END;
                    }
                } else if (state == EchoState.EXPECTING_LINE_END) {
                    if (character == '\n') {
                        return discardEcho(text, index + 1);
                    }
                    if (character == '\r') {
                        state = EchoState.EXPECTING_LINE_FEED;
                    } else {
                        return release(text, index + 1);
                    }
                } else if (state == EchoState.EXPECTING_LINE_FEED) {
                    if (character == '\n') {
                        return discardEcho(text, index + 1);
                    }
                    return release(text, index + 1);
                }
            }
            return "";
        }

        private String finish() {
            if (state == EchoState.RESOLVED || pending.isEmpty()) {
                return "";
            }
            String result = state == EchoState.MATCHING_COMMAND ? pending.toString() : "";
            pending.setLength(0);
            state = EchoState.RESOLVED;
            return result;
        }

        private String release(String text, int consumedCharacters) {
            String result = pending + text.substring(consumedCharacters);
            pending.setLength(0);
            state = EchoState.RESOLVED;
            return result;
        }

        private String discardEcho(String text, int consumedCharacters) {
            pending.setLength(0);
            state = EchoState.RESOLVED;
            return text.substring(consumedCharacters);
        }

        private enum EchoState {
            MATCHING_COMMAND,
            EXPECTING_LINE_END,
            EXPECTING_LINE_FEED,
            RESOLVED
        }
    }

    private static final class OutputAccumulator {
        private final int limit;
        private final int chunkBytes;
        private final StringBuilder text = new StringBuilder();
        private final List<CommandOutputBlock> blocks = new ArrayList<>();
        private List<CommandSpec> commands = List.of();
        private CommandSpec activeCommand;
        private StringBuilder commandText;
        private Instant commandStartedAt;
        private int totalReceivedBytes;
        private int commandReceivedBytes;
        private boolean truncated;

        private OutputAccumulator(int limit, int chunkBytes) {
            this.limit = limit;
            this.chunkBytes = chunkBytes;
        }

        private void initialize(CommandPlan commandPlan) {
            commands = commandPlan.commands();
        }

        private void startCommand(CommandSpec command) {
            activeCommand = command;
            commandText = new StringBuilder();
            commandStartedAt = Instant.now();
            commandReceivedBytes = 0;
        }

        private boolean emit(String value, ProgressListener listener, int pageCount) {
            if (activeCommand == null) {
                return truncated;
            }
            int offset = 0;
            while (offset < value.length()) {
                int allowed = Math.min(chunkBytes, limit - totalReceivedBytes);
                if (allowed == 0) {
                    truncated = true;
                    notifyProgress(listener, new OutputProgress(
                            activeCommand.commandIndex(), OutputStreamType.STDOUT, "",
                            commandReceivedBytes, pageCount, true));
                    return true;
                }
                int end = utf8PrefixEnd(value, offset, allowed);
                if (end == offset) {
                    truncated = true;
                    notifyProgress(listener, new OutputProgress(
                            activeCommand.commandIndex(), OutputStreamType.STDOUT, "",
                            commandReceivedBytes, pageCount, true));
                    return true;
                }
                String chunk = value.substring(offset, end);
                int bytes = chunk.getBytes(StandardCharsets.UTF_8).length;
                text.append(chunk);
                commandText.append(chunk);
                totalReceivedBytes += bytes;
                commandReceivedBytes += bytes;
                offset = end;
                boolean atLimit = totalReceivedBytes == limit;
                truncated |= atLimit;
                notifyProgress(listener, new OutputProgress(
                        activeCommand.commandIndex(), OutputStreamType.STDOUT, chunk,
                        commandReceivedBytes, pageCount, truncated));
                if (atLimit) {
                    return true;
                }
            }
            return false;
        }

        private void capture(String value) {
            if (activeCommand == null) {
                return;
            }
            int end = utf8PrefixEnd(value, 0, limit - totalReceivedBytes);
            if (end > 0) {
                String accepted = value.substring(0, end);
                text.append(accepted);
                commandText.append(accepted);
                int bytes = accepted.getBytes(StandardCharsets.UTF_8).length;
                totalReceivedBytes += bytes;
                commandReceivedBytes += bytes;
            }
            truncated |= end < value.length() || totalReceivedBytes == limit;
        }

        private void finishCommand(CommandBlockStatus status, Integer exitCode, String outcome, int pageCount) {
            if (activeCommand == null) {
                return;
            }
            blocks.add(new CommandOutputBlock(activeCommand.commandIndex(), activeCommand.commandText(), status,
                    commandText.toString(), "", commandReceivedBytes, pageCount, truncated,
                    exitCode, outcome, java.util.Map.of(), List.of(), commandStartedAt, Instant.now(), false));
            activeCommand = null;
            commandText = null;
            commandStartedAt = null;
            commandReceivedBytes = 0;
        }

        private void cancelRemaining() {
            if (activeCommand != null) {
                finishCommand(CommandBlockStatus.FAILED, null, "command execution stopped", 0);
            }
            for (int index = blocks.size(); index < commands.size(); index++) {
                CommandSpec command = commands.get(index);
                blocks.add(new CommandOutputBlock(command.commandIndex(), command.commandText(),
                        CommandBlockStatus.CANCELLED, "", "", 0, 0, false,
                        null, "not executed", java.util.Map.of(), List.of(), null, Instant.now(), false));
            }
        }

        private List<CommandOutputBlock> blocks() {
            return List.copyOf(blocks);
        }

        private String text() {
            return text.toString();
        }

        private boolean truncated() {
            return truncated;
        }
    }

    private static void notifyProgress(ProgressListener listener, OutputProgress progress) {
        try {
            listener.onProgress(progress);
        } catch (RuntimeException exception) {
            throw new ProgressListenerFailure(exception);
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

    private static final class RawTelnetClient extends TelnetClient {
        private OutputStream rawOutputStream() {
            if (_output_ == null) {
                throw new IllegalStateException("Telnet client is not connected");
            }
            return _output_;
        }
    }

    private record Session(TelnetClient client, InputStream input, OutputStream output,
                           Pattern commandPrompt, TelnetLineEnding lineEnding) implements AutoCloseable {
        private void writeLine(String value) {
            try {
                TelnetCommandExecutionAdapter.writeLine(output, value, lineEnding);
            } catch (IOException exception) {
                throw failure(ConnectionFailure.Code.CONNECTION_CLOSED, ConnectionFailure.Stage.EXECUTE,
                        CONNECTION_CLOSED_MESSAGE, exception);
            }
        }

        private void writeContinue() {
            try {
                // Flow-control input is never forwarded to the output listener or persistence layer.
                output.write(' ');
                output.flush();
            } catch (IOException exception) {
                throw failure(ConnectionFailure.Code.CONNECTION_CLOSED, ConnectionFailure.Stage.EXECUTE,
                        CONNECTION_CLOSED_MESSAGE, exception);
            }
        }

        @Override
        public void close() {
            if (client.isConnected()) {
                try {
                    output.flush();
                    output.close();
                } catch (IOException ignored) {
                    // The remote side may already have closed the session.
                }
            }
            disconnect(client);
        }
    }
}
