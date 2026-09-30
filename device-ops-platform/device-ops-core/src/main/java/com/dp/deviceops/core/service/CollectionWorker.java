package com.dp.deviceops.core.service;

import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.model.ConnectionFailure;
import com.dp.deviceops.core.model.ExecutionConnectionContext;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.core.model.CommandPlan;
import com.dp.deviceops.core.port.CollectionExecutionPersistencePort;
import com.dp.deviceops.core.port.CommandExecutionPort;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executes a single frozen target and persists only redacted evidence. */
public final class CollectionWorker {
    private final CommandExecutionPort command;
    private final CollectionExecutionPersistencePort persistence;
    private final Clock clock;
    public CollectionWorker(CommandExecutionPort command, CollectionExecutionPersistencePort persistence, Clock clock) {
        this.command = Objects.requireNonNull(command);
        this.persistence = Objects.requireNonNull(persistence);
        this.clock = Objects.requireNonNull(clock);
    }
    /** Executes work that has already been claimed and admitted for its connection key. */
    public void execute(WorkItem item) {
        Objects.requireNonNull(item);
        Evidence[] evidence = {Evidence.empty()};
        boolean[] terminal = {false};
        StreamingProgressPersistence[] progressPersistence = {null};
        try (ExecutionConnectionContext context = item.context()) {
            progressPersistence[0] = context.withCredentials(
                    (secret, passphrase) -> new StreamingProgressPersistence(
                            item, persistence, clock, secret, passphrase));
            update(item, CollectionStatus.CONNECTING, evidence[0]);
            CommandPlan commandPlan = CommandPlan.fromScript(item.script().content());
            persistence.initializeCommandBlocks(item.targetId(), item.workerId(),
                    commandPlan.commands().stream().map(CommandOutputBlock::pending).toList());
            CommandExecutionPort.CommandResult result;
            try {
                result = context.execute(
                        command,
                        item.script().content(),
                        item.timeout(),
                        progressPersistence[0]::onProgress);
            } catch (RuntimeException failure) {
                try {
                    progressPersistence[0].finishWithoutFinalResult();
                } catch (RuntimeException flushFailure) {
                    failure.addSuppressed(flushFailure);
                }
                throw failure;
            }
            progressPersistence[0].finishWithFinalResult(
                    result.stdout(),
                    result.stderr(),
                    result.truncated());
            List<CommandOutputBlock> safeBlocks = progressPersistence[0].sanitizeBlocks(result.commandBlocks());
            if (context.projection().executionMode() == CommandExecutionPort.ExecutionMode.SHELL) {
                safeBlocks = safeBlocks.stream().map(CliCommandRejection::classify).toList();
            }
            persistence.updateCommandBlocks(item.targetId(), item.workerId(), safeBlocks);
            evidence[0] = progressPersistence[0].evidence().withExitCode(result.exitCode());
            update(item, CollectionStatus.EXECUTING, evidence[0]);
            if (result.timedOut()) { update(item, CollectionStatus.TIMED_OUT, evidence[0].withOutcome(ConnectionFailure.Code.EXECUTION_TIMEOUT.name())); terminal[0] = true; return; }
            if (result.exitCode() != 0) { update(item, CollectionStatus.FAILED, evidence[0].withOutcome("non-zero exit code")); terminal[0] = true; return; }
            if (safeBlocks.stream().anyMatch(block -> "COMMAND_REJECTED".equals(block.outcome()))) {
                update(item, CollectionStatus.FAILED, evidence[0].withOutcome("COMMAND_REJECTED"));
                terminal[0] = true;
                return;
            }
            update(item, CollectionStatus.PARSING, evidence[0]);
            try {
                var parser = item.parsers().find(item.script().parserType()).orElseThrow(() -> new IllegalStateException("parser is unavailable"));
                List<CommandOutputBlock> parsedBlocks = new ArrayList<>();
                Map<String, String> aggregateFacts = new LinkedHashMap<>();
                List<String> aggregateWarnings = new ArrayList<>();
                for (CommandOutputBlock block : safeBlocks) {
                    try {
                        var parsed = parser.parse(block.stdout(), item.script().parserConfig());
                        parsed.values().forEach((key, value) ->
                                aggregateFacts.put("command." + block.commandIndex() + "." + key, value));
                        aggregateWarnings.addAll(parsed.warnings());
                        parsedBlocks.add(withParsing(block, parsed.values(), parsed.warnings()));
                    } catch (RuntimeException parseFailure) {
                        if (Thread.currentThread().isInterrupted()) throw parseFailure;
                        String warning = "parser failed";
                        aggregateWarnings.add(warning);
                        parsedBlocks.add(withParsing(block, Map.of(), List.of(warning)));
                    }
                }
                persistence.updateCommandBlocks(item.targetId(), item.workerId(), parsedBlocks);
                String outcome = aggregateWarnings.isEmpty() ? null : String.join("; ", aggregateWarnings);
                update(item, aggregateWarnings.isEmpty() ? CollectionStatus.SUCCEEDED : CollectionStatus.PARTIAL_SUCCESS,
                        evidence[0].withFacts(aggregateFacts).withOutcome(outcome)); terminal[0] = true;
            } catch (ExecutionLeaseLostException leaseLost) { return;
            } catch (RuntimeException parseFailure) {
                if (Thread.currentThread().isInterrupted()) throw parseFailure;
                update(item, CollectionStatus.PARTIAL_SUCCESS, evidence[0].withOutcome("parser failed")); terminal[0] = true;
            }
        } catch (ExecutionLeaseLostException leaseLost) { return;
        } catch (ConnectionFailure failure) {
            if (item.context().isCancellationRequested()) return;
            if (Thread.currentThread().isInterrupted()) throw failure;
            if (!terminal[0]) {
                try { update(item, CollectionStatus.FAILED, progressEvidence(progressPersistence[0], evidence[0]).withOutcome(failure.code().name())); }
                catch (ExecutionLeaseLostException ignored) { }
            }
        } catch (RuntimeException failure) {
            if (item.context().isCancellationRequested()) return;
            if (Thread.currentThread().isInterrupted()) throw failure;
            if (!terminal[0]) {
                try { update(item, CollectionStatus.FAILED, progressEvidence(progressPersistence[0], evidence[0]).withOutcome("execution failed")); }
                catch (ExecutionLeaseLostException ignored) { }
            }
        } finally {
            if (progressPersistence[0] != null) progressPersistence[0].close();
        }
    }
    private static Evidence progressEvidence(StreamingProgressPersistence progress, Evidence fallback) {
        if (progress == null) {
            return fallback;
        }
        try {
            progress.finishWithoutFinalResult();
        } catch (RuntimeException ignored) {
            // Safe chunks are recorded before persistence, so failure evidence remains recoverable.
        }
        return progress.evidence();
    }
    private static long utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
    private void update(WorkItem item, CollectionStatus status, Evidence evidence) {
        item.context().checkCancellation();
        persistence.updateTarget(item.targetId(), item.workerId(), status, evidence.stdout(), evidence.stderr(), evidence.exitCode(), evidence.outcome(), evidence.truncated(), evidence.facts());
    }
    private static CommandOutputBlock withParsing(
            CommandOutputBlock block,
            Map<String, String> facts,
            List<String> warnings) {
        return new CommandOutputBlock(block.commandIndex(), block.commandText(), block.status(),
                block.stdout(), block.stderr(), block.receivedBytes(), block.pageCount(), block.truncated(),
                block.exitCode(), block.outcome(), facts, warnings,
                block.startedAt(), block.completedAt(), block.legacy());
    }
    public record WorkItem(long targetId, String workerId, ExecutionConnectionContext context, ScriptArtifact script, Duration timeout, Duration parseTimeout, Duration leaseGrace, OutputParserRegistry parsers) {
        public WorkItem { Objects.requireNonNull(workerId); Objects.requireNonNull(context); Objects.requireNonNull(script); Objects.requireNonNull(timeout); Objects.requireNonNull(parseTimeout); Objects.requireNonNull(leaseGrace); Objects.requireNonNull(parsers); if (timeout.isNegative() || timeout.isZero() || parseTimeout.isNegative() || leaseGrace.isNegative()) throw new IllegalArgumentException("execution durations are invalid"); }
        public Duration leaseDuration() { return context.projection().connectTimeout().plus(timeout).plus(parseTimeout).plus(leaseGrace); }
        public Duration leaseDuration(Duration connectionWaitTimeout) { return leaseDuration().plus(connectionWaitTimeout); }
        public CommandExecutionPort.ConnectionSpec connectionSpec() {
            ExecutionConnectionContext.ConnectionProjection projection = context.projection();
            return new CommandExecutionPort.ConnectionSpec(
                    projection.protocol(), projection.host(), projection.port(), projection.username(),
                    projection.authenticationType(), projection.executionMode(),
                    projection.expectedHostKeyFingerprint(), projection.telnetPrompts(),
                    projection.serialParams(), projection.serialPrompts(), projection.connectTimeout());
        }
    }
    private record Evidence(String stdout, String stderr, Integer exitCode, boolean truncated, Map<String, String> facts, String outcome) {
        private Evidence(String stdout, String stderr, Integer exitCode, boolean truncated, Map<String, String> facts) { this(stdout, stderr, exitCode, truncated, facts, null); }
        static Evidence empty() { return new Evidence("", "", null, false, Map.of()); }
        Evidence withExitCode(Integer value) { return new Evidence(stdout, stderr, value, truncated, facts, outcome); }
        Evidence withFacts(Map<String, String> values) { return new Evidence(stdout, stderr, exitCode, truncated, values, outcome); }
        Evidence withOutcome(String value) { return new Evidence(stdout, stderr, exitCode, truncated, facts, value); }
    }

    private static final class StreamingProgressPersistence implements AutoCloseable {
        private static final int MAX_RAW_CHARACTERS = 8 * 1024 * 1024;
        private static final long MAX_PERSISTED_OUTPUT_BYTES = 8L * 1024 * 1024;
        private static final int MAX_EVENT_BYTES = 8 * 1024;
        private static final int FINAL_RAW_CHUNK_CHARACTERS = 8 * 1024;
        private static final Duration SNAPSHOT_INTERVAL = Duration.ofMillis(500);
        private final WorkItem item;
        private final CollectionExecutionPersistencePort persistence;
        private final Clock clock;
        private final EnumMap<CommandExecutionPort.OutputStreamType, StreamState> streams =
                new EnumMap<>(CommandExecutionPort.OutputStreamType.class);
        private final char[] secret;
        private final char[] passphrase;
        private long persistedBytes;
        private Instant lastSnapshotAt;
        private boolean budgetTruncated;
        private boolean truncationEventRecorded;
        private boolean finished;
        private int lastCommandIndex = 1;

        private StreamingProgressPersistence(
                WorkItem item,
                CollectionExecutionPersistencePort persistence,
                Clock clock,
                char[] secret,
                char[] passphrase) {
            this.item = item;
            this.persistence = persistence;
            this.clock = clock;
            this.secret = secret == null ? new char[0] : Arrays.copyOf(secret, secret.length);
            this.passphrase = passphrase == null ? new char[0] : Arrays.copyOf(passphrase, passphrase.length);
            streams.put(CommandExecutionPort.OutputStreamType.STDOUT, new StreamState(secret, passphrase));
            streams.put(CommandExecutionPort.OutputStreamType.STDERR, new StreamState(secret, passphrase));
        }

        private synchronized void onProgress(CommandExecutionPort.OutputProgress progress) {
            Objects.requireNonNull(progress, "progress");
            if (finished) {
                throw new IllegalStateException("output progress arrived after stream completion");
            }
            StreamState state = streams.get(Objects.requireNonNull(progress.streamType(), "progress streamType"));
            lastCommandIndex = progress.commandIndex();
            String content = progress.content() == null ? "" : progress.content();
            state.appendRaw(content);
            state.receivedBytes = progress.receivedBytes();
            state.pageCount = progress.pageCount();
            state.truncated |= progress.truncated();
            recordAndPersist(progress.commandIndex(), progress.streamType(), state,
                    state.redactor.accept(content), true);
        }

        private synchronized void finishWithoutFinalResult() {
            if (finished) {
                return;
            }
            StreamState stdout = streams.get(CommandExecutionPort.OutputStreamType.STDOUT);
            StreamState stderr = streams.get(CommandExecutionPort.OutputStreamType.STDERR);
            String stdoutAddition = stdout.redactor.flush();
            String stderrAddition = stderr.redactor.flush();
            recordAndPersist(lastCommandIndex, CommandExecutionPort.OutputStreamType.STDOUT, stdout, stdoutAddition, false);
            recordAndPersist(lastCommandIndex, CommandExecutionPort.OutputStreamType.STDERR, stderr, stderrAddition, false);
            finished = true;
            forceSnapshot();
        }

        private synchronized void finishWithFinalResult(
                String finalRawStdout,
                String finalRawStderr,
                boolean truncated) {
            if (finished) {
                throw new IllegalStateException("output progress is already complete");
            }
            StreamState stdout = streams.get(CommandExecutionPort.OutputStreamType.STDOUT);
            StreamState stderr = streams.get(CommandExecutionPort.OutputStreamType.STDERR);
            requireRawPrefix(stdout.rawProgress, finalRawStdout, CommandExecutionPort.OutputStreamType.STDOUT);
            requireRawPrefix(stderr.rawProgress, finalRawStderr, CommandExecutionPort.OutputStreamType.STDERR);

            stdout.receivedBytes = Math.max(stdout.receivedBytes, utf8Length(finalRawStdout));
            stderr.receivedBytes = Math.max(stderr.receivedBytes, utf8Length(finalRawStderr));
            stdout.truncated |= truncated;
            stderr.truncated |= truncated;
            completeFinalStream(CommandExecutionPort.OutputStreamType.STDOUT, stdout, finalRawStdout);
            completeFinalStream(CommandExecutionPort.OutputStreamType.STDERR, stderr, finalRawStderr);
            finished = true;
            forceSnapshot();
        }

        private static void requireRawPrefix(
                CharSequence received,
                String finalRaw,
                CommandExecutionPort.OutputStreamType streamType) {
            if (received.length() > finalRaw.length()) {
                throw new IllegalStateException("incremental raw output exceeds final " + streamType + " output");
            }
            for (int index = 0; index < received.length(); index++) {
                if (received.charAt(index) != finalRaw.charAt(index)) {
                    throw new IllegalStateException(
                            "incremental raw output does not match final " + streamType + " output");
                }
            }
        }

        private void recordAndPersist(
                int commandIndex,
                CommandExecutionPort.OutputStreamType streamType,
                StreamState state,
                String safe,
                boolean snapshotDuringExecution) {
            persist(commandIndex, streamType, state, safe, snapshotDuringExecution);
        }

        private void completeFinalStream(
                CommandExecutionPort.OutputStreamType streamType,
                StreamState state,
                String finalRaw) {
            int offset = state.rawProgress.length();
            while (offset < finalRaw.length() && !budgetTruncated) {
                int end = Math.min(finalRaw.length(), offset + FINAL_RAW_CHUNK_CHARACTERS);
                if (end < finalRaw.length()
                        && Character.isHighSurrogate(finalRaw.charAt(end - 1))
                        && Character.isLowSurrogate(finalRaw.charAt(end))) {
                    end--;
                }
                recordAndPersist(lastCommandIndex,
                        streamType,
                        state,
                        state.redactor.accept(finalRaw.substring(offset, end)),
                        false);
                offset = end;
            }
            if (!budgetTruncated) {
                recordAndPersist(lastCommandIndex, streamType, state, state.redactor.flush(), false);
            }
        }

        private void persist(
                int commandIndex,
                CommandExecutionPort.OutputStreamType streamType,
                StreamState state,
                String safe,
                boolean snapshotDuringExecution) {
            int offset = 0;
            while (offset < safe.length() && persistedBytes < MAX_PERSISTED_OUTPUT_BYTES) {
                int maximumBytes = (int) Math.min(
                        MAX_EVENT_BYTES, MAX_PERSISTED_OUTPUT_BYTES - persistedBytes);
                int end = utf8ChunkEnd(safe, offset, maximumBytes);
                if (end == offset) {
                    budgetTruncated = true;
                    break;
                }
                String chunk = safe.substring(offset, end);
                int chunkBytes = Math.toIntExact(utf8Length(chunk));
                persistedBytes += chunkBytes;
                state.persistedOutput.append(chunk);
                offset = end;
                boolean budgetExhausted = persistedBytes == MAX_PERSISTED_OUTPUT_BYTES;
                if (budgetExhausted) {
                    budgetTruncated = true;
                }
                appendEvent(commandIndex, streamType, state, chunk, isTruncated());
                if (snapshotDuringExecution) {
                    snapshotIfDue();
                }
                if (budgetExhausted) {
                    break;
                }
            }
            if (offset < safe.length()) {
                budgetTruncated = true;
            }
            if (isTruncated() && !truncationEventRecorded) {
                appendEvent(commandIndex, streamType, state, "", true);
                if (snapshotDuringExecution) {
                    snapshotIfDue();
                }
            }
        }

        private void appendEvent(
                int commandIndex,
                CommandExecutionPort.OutputStreamType streamType,
                StreamState state,
                String content,
                boolean truncated) {
            persistence.appendOutput(
                    item.targetId(),
                    item.workerId(),
                    commandIndex,
                    streamType,
                    content,
                    state.receivedBytes,
                    state.pageCount,
                    truncated,
                    clock.instant());
            truncationEventRecorded |= truncated;
        }

        private void snapshotIfDue() {
            Instant now = clock.instant();
            if (lastSnapshotAt == null || !now.isBefore(lastSnapshotAt.plus(SNAPSHOT_INTERVAL))) {
                persistSnapshot();
                lastSnapshotAt = now;
            }
        }

        private void forceSnapshot() {
            persistSnapshot();
            lastSnapshotAt = clock.instant();
        }

        private void persistSnapshot() {
            StreamState stdout = streams.get(CommandExecutionPort.OutputStreamType.STDOUT);
            StreamState stderr = streams.get(CommandExecutionPort.OutputStreamType.STDERR);
            persistence.updateOutputSnapshot(
                    item.targetId(),
                    item.workerId(),
                    stdout.persistedOutput.toString(),
                    stderr.persistedOutput.toString(),
                    isTruncated());
        }

        private boolean isTruncated() {
            return budgetTruncated || streams.values().stream().anyMatch(stream -> stream.truncated);
        }

        private static int utf8ChunkEnd(String value, int start, int maximumBytes) {
            int index = start;
            int bytes = 0;
            while (index < value.length()) {
                int codePoint = value.codePointAt(index);
                int codePointBytes = utf8CodePointLength(codePoint);
                if (bytes + codePointBytes > maximumBytes) {
                    break;
                }
                bytes += codePointBytes;
                index += Character.charCount(codePoint);
            }
            return index;
        }

        private static int utf8CodePointLength(int codePoint) {
            if (codePoint <= 0x7f) {
                return 1;
            }
            if (codePoint <= 0x7ff) {
                return 2;
            }
            if (codePoint <= 0xffff) {
                return Character.isSurrogate((char) codePoint) ? 1 : 3;
            }
            return 4;
        }

        private synchronized Evidence evidence() {
            StreamState stdout = streams.get(CommandExecutionPort.OutputStreamType.STDOUT);
            StreamState stderr = streams.get(CommandExecutionPort.OutputStreamType.STDERR);
            return new Evidence(
                    stdout.persistedOutput.toString(),
                    stderr.persistedOutput.toString(),
                    null,
                    isTruncated(),
                    Map.of());
        }

        private synchronized List<CommandOutputBlock> sanitizeBlocks(List<CommandOutputBlock> rawBlocks) {
            List<CommandOutputBlock> safe = new ArrayList<>();
            for (CommandOutputBlock block : rawBlocks) {
                safe.add(new CommandOutputBlock(block.commandIndex(), block.commandText(), block.status(),
                        redact(block.stdout()), redact(block.stderr()), block.receivedBytes(), block.pageCount(),
                        block.truncated(), block.exitCode(), block.outcome(), Map.of(), List.of(),
                        block.startedAt(), block.completedAt(), block.legacy()));
            }
            return List.copyOf(safe);
        }

        private String redact(String raw) {
            try (StreamingOutputRedactor redactor = new StreamingOutputRedactor(secret, passphrase)) {
                return redactor.accept(raw) + redactor.flush();
            }
        }

        @Override
        public synchronized void close() {
            for (StreamState state : streams.values()) {
                state.close();
            }
            Arrays.fill(secret, '\0');
            Arrays.fill(passphrase, '\0');
        }

        private static final class StreamState implements AutoCloseable {
            private final StreamingOutputRedactor redactor;
            private final StringBuilder persistedOutput = new StringBuilder();
            private final StringBuilder rawProgress = new StringBuilder();
            private long receivedBytes;
            private int pageCount;
            private boolean truncated;

            private StreamState(char[] secret, char[] passphrase) {
                redactor = new StreamingOutputRedactor(secret, passphrase);
            }

            private void appendRaw(String content) {
                if (rawProgress.length() + content.length() > MAX_RAW_CHARACTERS) {
                    throw new IllegalStateException("incremental raw output exceeds reconciliation buffer");
                }
                rawProgress.append(content);
            }

            @Override
            public void close() {
                redactor.close();
                clear(rawProgress);
                clear(persistedOutput);
            }

            private static void clear(StringBuilder value) {
                for (int index = 0; index < value.length(); index++) {
                    value.setCharAt(index, '\0');
                }
                value.setLength(0);
            }
        }
    }
}
