package com.dp.deviceops.parser.semantic.cli;

import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CommandOutputBlockInput(
        int commandIndex,
        String commandText,
        CommandBlockStatus status,
        String stdout,
        String stderr,
        Long receivedBytes,
        Integer pageCount,
        Boolean truncated,
        Integer exitCode,
        String outcome,
        Map<String, String> parsedFacts,
        List<String> parseWarnings,
        Instant startedAt,
        Instant completedAt,
        Boolean legacy) {

    public CommandOutputBlock toDomain() {
        String safeStdout = stdout == null ? "" : stdout;
        String safeStderr = stderr == null ? "" : stderr;
        long bytes = receivedBytes == null
                ? utf8Length(safeStdout) + utf8Length(safeStderr)
                : receivedBytes;
        return new CommandOutputBlock(commandIndex, commandText,
                status, safeStdout, safeStderr, bytes,
                pageCount == null ? 0 : pageCount,
                Boolean.TRUE.equals(truncated), exitCode, outcome,
                parsedFacts == null ? Map.of() : parsedFacts,
                parseWarnings == null ? List.of() : parseWarnings,
                startedAt, completedAt, Boolean.TRUE.equals(legacy));
    }

    private static long utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
