package com.dp.deviceops.adapter.serial;

import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.core.model.CommandPlan;
import com.dp.deviceops.core.model.CommandPlan.CommandSpec;
import com.dp.deviceops.core.model.ConnectionFailure;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.ProtocolCommandExecutionAdapter;
import com.dp.deviceops.core.terminal.TerminalPagerController;
import com.dp.deviceops.core.terminal.TerminalTextProcessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SerialCommandExecutionAdapter implements ProtocolCommandExecutionAdapter {

    static final long READ_SLICE_MILLIS = 100;
    static final Duration PAGER_TERMINAL_SETTLE = Duration.ofMillis(250);
    private static final int PROMPT_CAPTURE_LIMIT = 8 * 1024;
    private static final String PROTOCOL_DISABLED_MESSAGE = "connection protocol is disabled";
    private static final String SERIAL_PORT_FAILED_MESSAGE = "serial port is unavailable";
    private static final String CONNECT_TIMEOUT_MESSAGE = "connection timed out";
    private static final String PROMPT_NOT_FOUND_MESSAGE = "expected serial console prompt was not found";
    private static final String AUTH_FAILED_MESSAGE = "authentication failed";
    private static final String CONNECTION_CLOSED_MESSAGE = "connection closed during command execution";

    private final boolean enabled;
    private final int maxOutputBytes;
    private final int eventChunkBytes;
    private final int maxPages;

    public SerialCommandExecutionAdapter(boolean enabled, int maxOutputBytes, int eventChunkBytes, int maxPages) {
        if (maxOutputBytes < 1) {
            throw new IllegalArgumentException("maxOutputBytes must be positive");
        }
        if (eventChunkBytes < 1 || eventChunkBytes > 8_192) {
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
        return ConnectionProtocol.SERIAL;
    }

    @Override
    public void test(ConnectionSpec connection, char[] secret, char[] passphrase) {
        requireEnabled();
        requireSerial(connection, passphrase);
        char[] password = copyRequiredSecret(secret);
        try (SerialTransport transport = new JdkSerialTransport(connection.host())) {
            login(connection, password, transport);
        } catch (ConnectionFailure failure) {
            throw failure;
        } catch (RuntimeException exception) {
            throw failure(ConnectionFailure.Code.UNREACHABLE, ConnectionFailure.Stage.CONNECT,
                    SERIAL_PORT_FAILED_MESSAGE, exception);
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
        requireSerial(connection, passphrase);
        Objects.requireNonNull(script, "script");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(listener, "listener");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        char[] password = copyRequiredSecret(secret);
        try (SerialTransport transport = new JdkSerialTransport(connection.host())) {
            return executeTransport(connection, password, transport, script, timeout, listener);
        } finally {
            clear(password);
        }
    }

    /** Test seam: identical to the private executeTransport but with an injected transport. */
    CommandResult executeTransport(ConnectionSpec connection, char[] secret, SerialTransport transport,
                                   String script, Duration timeout) {
        return executeTransport(connection, secret, transport, script, timeout, ProgressListener.noop());
    }

    private CommandResult executeTransport(ConnectionSpec connection, char[] password, SerialTransport transport,
                                           String script, Duration timeout, ProgressListener listener) {
        long started = System.nanoTime();
        OutputAccumulator output = new OutputAccumulator(maxOutputBytes, eventChunkBytes);
        try {
            login(connection, password, transport);
            Deadline deadline = new Deadline(timeout);
            Pattern commandPrompt = Pattern.compile(connection.serialPrompts().command());
            byte[] lineEnding = lineEndingBytes(connection.serialPrompts().lineEnding());
            ExecutionState execution = new ExecutionState(commandPrompt, maxPages);
            TerminalPagerController pager = new TerminalPagerController(PAGER_TERMINAL_SETTLE, maxPages);
            byte[] buffer = new byte[eventChunkBytes];
            CommandPlan commandPlan = CommandPlan.fromScript(script);
            output.initialize(commandPlan);
            for (CommandSpec command : commandPlan.commands()) {
                execution.startCommand(command);
                output.startCommand(command);
                transport.write(join(command.commandText().toCharArray(), lineEnding));
                while (true) {
                    int read = readSlice(transport, buffer, deadline);
                    if (read == 0) {
                        if (pager.continuationReady()) {
                            transport.write(new byte[]{' '});
                            pager.markContinuationSent();
                            execution.updatePageCount(pager.pageCount());
                        }
                        continue;
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
            return result(0, output, false, started);
        } catch (ProgressListenerFailure failure) {
            throw failure.original();
        } catch (DeadlineExpired timeoutFailure) {
            return result(-1, output, true, started);
        } catch (ConnectionFailure failure) {
            transport.close();
            throw failure;
        } catch (IOException exception) {
            transport.close();
            throw failure(ConnectionFailure.Code.CONNECTION_CLOSED, ConnectionFailure.Stage.EXECUTE,
                    CONNECTION_CLOSED_MESSAGE, exception);
        }
    }

    private static int readSlice(SerialTransport transport, byte[] buffer, Deadline deadline) throws IOException {
        int read = transport.read(buffer, READ_SLICE_MILLIS);
        if (read > 0) {
            return read;
        }
        deadline.check();
        return 0;
    }

    private static CommandResult result(int exitCode, OutputAccumulator output, boolean timedOut, long started) {
        return new CommandResult(exitCode, output.stdout(), "", timedOut, output.truncated(),
                (System.nanoTime() - started) / 1_000_000, output.blocks());
    }

    /** Test seam: identical to test() but with an injected transport. */
    void testTransport(ConnectionSpec connection, char[] secret, SerialTransport transport) {
        requireEnabled();
        requireSerial(connection, null);
        char[] password = copyRequiredSecret(secret);
        try {
            login(connection, password, transport);
        } finally {
            clear(password);
        }
    }

    private void login(ConnectionSpec connection, char[] password, SerialTransport transport) {
        Deadline deadline = new Deadline(connection.connectTimeout());
        try {
            transport.open(connection.serialParams());
            Pattern loginPrompt = Pattern.compile(connection.serialPrompts().login());
            Pattern passwordPrompt = Pattern.compile(connection.serialPrompts().password());
            Pattern commandPrompt = Pattern.compile(connection.serialPrompts().command());
            byte[] lineEnding = lineEndingBytes(connection.serialPrompts().lineEnding());
            readLoginPrompt(transport, loginPrompt, deadline, "username");
            transport.write(join(connection.username().toCharArray(), lineEnding));
            readLoginPrompt(transport, passwordPrompt, deadline, "password");
            transport.write(join(password, lineEnding));
            try {
                readUntilPrompt(transport, commandPrompt, deadline);
            } catch (PromptRejected | PromptClosed exception) {
                throw failure(ConnectionFailure.Code.AUTH_FAILED, ConnectionFailure.Stage.AUTHENTICATE,
                        AUTH_FAILED_MESSAGE, exception);
            } catch (PromptTimeout exception) {
                throw failure(ConnectionFailure.Code.PROMPT_NOT_FOUND, ConnectionFailure.Stage.LOGIN,
                        PROMPT_NOT_FOUND_MESSAGE, exception);
            }
        } catch (ConnectionFailure failure) {
            transport.close();
            throw failure;
        } catch (IOException | RuntimeException exception) {
            transport.close();
            if (deadline.expired()) {
                throw failure(ConnectionFailure.Code.CONNECT_TIMEOUT, ConnectionFailure.Stage.CONNECT,
                        CONNECT_TIMEOUT_MESSAGE, exception);
            }
            throw failure(ConnectionFailure.Code.UNREACHABLE, ConnectionFailure.Stage.CONNECT,
                    SERIAL_PORT_FAILED_MESSAGE, exception);
        }
    }

    private static void readLoginPrompt(SerialTransport transport, Pattern prompt, Deadline deadline,
                                        String stage) {
        try {
            readUntilPrompt(transport, prompt, deadline);
        } catch (PromptRejected | PromptClosed exception) {
            throw failure(ConnectionFailure.Code.AUTH_FAILED, ConnectionFailure.Stage.AUTHENTICATE,
                    AUTH_FAILED_MESSAGE, exception);
        } catch (PromptTimeout exception) {
            throw failure(ConnectionFailure.Code.PROMPT_NOT_FOUND, ConnectionFailure.Stage.LOGIN,
                    PROMPT_NOT_FOUND_MESSAGE, exception);
        }
    }

    private static void readUntilPrompt(SerialTransport transport, Pattern prompt, Deadline deadline)
            throws PromptTimeout, PromptRejected, PromptClosed {
        StringBuilder window = new StringBuilder();
        byte[] buffer = new byte[1_024];
        while (true) {
            int read;
            try {
                read = transport.read(buffer, READ_SLICE_MILLIS);
            } catch (IOException exception) {
                throw new PromptClosed(window.toString());
            }
            if (read > 0) {
                window.append(new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8));
                if (window.length() > PROMPT_CAPTURE_LIMIT) {
                    window.delete(0, window.length() - PROMPT_CAPTURE_LIMIT);
                }
                if (prompt.matcher(window).find()) {
                    return;
                }
                if (isRejection(window)) {
                    throw new PromptRejected(window.toString());
                }
            }
            try {
                deadline.check();
            } catch (DeadlineExpired exception) {
                // Waiting for a prompt that never arrives is a prompt failure, not a connect failure.
                throw new PromptTimeout();
            }
        }
    }

    private static boolean isRejection(CharSequence window) {
        String lower = window.toString().toLowerCase(Locale.ROOT);
        return lower.contains("invalid") || lower.contains("incorrect")
                || lower.contains("denied") || lower.contains("fail")
                || lower.contains("错误") || lower.contains("失败");
    }

    static byte[] lineEndingBytes(CommandExecutionPort.TelnetLineEnding lineEnding) {
        return switch (lineEnding == null ? CommandExecutionPort.TelnetLineEnding.AUTO : lineEnding) {
            case CRLF -> new byte[]{'\r', '\n'};
            case CR, AUTO -> new byte[]{'\r'};
            case LF -> new byte[]{'\n'};
        };
    }

    private static byte[] join(char[] value, byte[] lineEnding) {
        byte[] text = new String(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] joined = new byte[text.length + lineEnding.length];
        System.arraycopy(text, 0, joined, 0, text.length);
        System.arraycopy(lineEnding, 0, joined, text.length, lineEnding.length);
        return joined;
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ConnectionFailure(ConnectionFailure.Code.PROTOCOL_DISABLED, ConnectionFailure.Stage.CONNECT,
                    PROTOCOL_DISABLED_MESSAGE);
        }
    }

    private static void requireSerial(ConnectionSpec connection, char[] passphrase) {
        Objects.requireNonNull(connection, "connection");
        if (connection.protocol() != ConnectionProtocol.SERIAL) {
            throw new ConnectionFailure(ConnectionFailure.Code.PROTOCOL_DISABLED, ConnectionFailure.Stage.CONNECT,
                    "connection protocol is not serial");
        }
        if (connection.serialPrompts() == null || connection.serialParams() == null) {
            throw new ConnectionFailure(ConnectionFailure.Code.PROTOCOL_DISABLED, ConnectionFailure.Stage.CONNECT,
                    "serial connection is incomplete");
        }
        if (passphrase != null && passphrase.length > 0) {
            throw new ConnectionFailure(ConnectionFailure.Code.PROTOCOL_DISABLED, ConnectionFailure.Stage.CONNECT,
                    "serial connections do not use a key passphrase");
        }
    }

    private static char[] copyRequiredSecret(char[] secret) {
        if (secret == null || secret.length == 0) {
            throw new ConnectionFailure(ConnectionFailure.Code.AUTH_FAILED, ConnectionFailure.Stage.AUTHENTICATE,
                    "credential secret is required");
        }
        char[] copy = Arrays.copyOf(secret, secret.length);
        Arrays.fill(secret, '\0');
        return copy;
    }

    private static void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    private static ConnectionFailure failure(ConnectionFailure.Code code, ConnectionFailure.Stage stage,
                                             String message, Throwable cause) {
        return new ConnectionFailure(code, stage, message, cause);
    }

    private static final class PromptTimeout extends IOException {
    }

    private static final class PromptRejected extends IOException {
        private PromptRejected(String partial) {
            super(partial);
        }
    }

    private static final class PromptClosed extends IOException {
        private PromptClosed(String partial) {
            super(partial);
        }
    }

    private static final class ExecutionState {
        private final TerminalTextProcessor terminal;
        private String pendingEchoCommand;
        private int pageCount;

        private ExecutionState(Pattern commandPrompt, int maxPages) {
            terminal = new TerminalTextProcessor(commandPrompt, maxPages);
        }

        private void startCommand(CommandSpec command) {
            pendingEchoCommand = command.commandText();
            pageCount = 0;
        }

        private TerminalTextProcessor.Decision accept(byte[] bytes, int length) {
            TerminalTextProcessor.Decision decision = terminal.accept(bytes, length);
            pageCount = decision.pageCount();
            return decision;
        }

        private String filter(String text) {
            String command = pendingEchoCommand;
            if (command == null || text.isEmpty()) {
                return text;
            }
            return text.replace(command.strip(), "");
        }

        private String finishCommand() {
            pendingEchoCommand = null;
            return "";
        }

        private void updatePageCount(int observedPageCount) {
            pageCount = Math.max(pageCount, observedPageCount);
        }

        private int pageCount() {
            return pageCount;
        }
    }

    private static final class OutputAccumulator {
        private final int limit;
        private final int chunkBytes;
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder commandText = new StringBuilder();
        private final List<CommandOutputBlock> blocks = new ArrayList<>();
        private CommandSpec activeCommand;
        private long totalReceivedBytes;
        private long commandReceivedBytes;
        private boolean truncated;

        private OutputAccumulator(int limit, int chunkBytes) {
            this.limit = limit;
            this.chunkBytes = chunkBytes;
        }

        private void initialize(CommandPlan plan) {
            for (CommandSpec command : plan.commands()) {
                blocks.add(pending(command));
            }
        }

        private static CommandOutputBlock pending(CommandSpec command) {
            return new CommandOutputBlock(command.commandIndex(), command.commandText(),
                    CommandBlockStatus.PENDING, "", "", 0, 0, false, null, null, Map.of(),
                    List.of(), null, null, false);
        }

        private void startCommand(CommandSpec command) {
            activeCommand = command;
            commandText.setLength(0);
            commandReceivedBytes = 0;
        }

        private boolean emit(String content, ProgressListener listener, int pageCount) {
            if (activeCommand == null || content == null || content.isEmpty()) {
                return truncated;
            }
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (totalReceivedBytes + bytes.length > limit) {
                truncated = true;
                notifyProgress(listener, new OutputProgress(activeCommand.commandIndex(),
                        OutputStreamType.STDOUT, "", commandReceivedBytes, pageCount, true));
                return true;
            }
            totalReceivedBytes += bytes.length;
            commandReceivedBytes += bytes.length;
            text.append(content);
            commandText.append(content);
            blocks.set(activeCommand.commandIndex() - 1, liveBlock(pageCount));
            notifyProgress(listener, new OutputProgress(activeCommand.commandIndex(),
                    OutputStreamType.STDOUT, chunk(content), commandReceivedBytes, pageCount, truncated));
            return false;
        }

        private CommandOutputBlock liveBlock(int pageCount) {
            return new CommandOutputBlock(activeCommand.commandIndex(), activeCommand.commandText(),
                    CommandBlockStatus.RUNNING, commandText.toString(), "", commandReceivedBytes, pageCount,
                    truncated, null, null, Map.of(), List.of(), null, null, false);
        }

        private void finishCommand(CommandBlockStatus status, Integer exitCode, String outcome, int pageCount) {
            if (activeCommand == null) {
                return;
            }
            blocks.set(activeCommand.commandIndex() - 1,
                    new CommandOutputBlock(activeCommand.commandIndex(), activeCommand.commandText(),
                            status, commandText.toString(), "", commandReceivedBytes, pageCount, truncated,
                            exitCode, outcome, Map.of(), List.of(), null, null, false));
            activeCommand = null;
            commandText.setLength(0);
        }

        private void notifyProgress(ProgressListener listener, OutputProgress progress) {
            try {
                listener.onProgress(progress);
            } catch (RuntimeException failure) {
                throw new ProgressListenerFailure(failure);
            }
        }

        private String chunk(String content) {
            return content.length() <= chunkBytes ? content : content.substring(0, chunkBytes);
        }

        private String stdout() {
            return text.toString();
        }

        private boolean truncated() {
            return truncated;
        }

        private List<CommandOutputBlock> blocks() {
            return List.copyOf(blocks);
        }
    }

    private static final class ProgressListenerFailure extends RuntimeException {
        private final RuntimeException original;

        private ProgressListenerFailure(RuntimeException original) {
            this.original = original;
        }

        private RuntimeException original() {
            return original;
        }
    }

    private static final class DeadlineExpired extends RuntimeException {
    }

    private static final class Deadline {
        private final long deadlineNanos;

        private Deadline(Duration timeout) {
            this.deadlineNanos = System.nanoTime() + timeout.toNanos();
        }

        private long remainingMillis() {
            return Math.max(0, (deadlineNanos - System.nanoTime()) / 1_000_000);
        }

        private boolean expired() {
            return System.nanoTime() >= deadlineNanos;
        }

        private void check() {
            if (expired()) {
                throw new DeadlineExpired();
            }
        }
    }
}
