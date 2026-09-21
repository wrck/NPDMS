package com.dp.deviceops.core.model;

import com.dp.deviceops.core.model.CommandPlan.CommandSpec;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable evidence produced by exactly one normalized command line. */
public record CommandOutputBlock(
        int commandIndex,
        String commandText,
        CommandBlockStatus status,
        String stdout,
        String stderr,
        long receivedBytes,
        int pageCount,
        boolean truncated,
        Integer exitCode,
        String outcome,
        Map<String, String> parsedFacts,
        List<String> parseWarnings,
        Instant startedAt,
        Instant completedAt,
        boolean legacy) {

    public CommandOutputBlock {
        if (commandIndex < 1) {
            throw new IllegalArgumentException("commandIndex must be positive");
        }
        commandText = Objects.requireNonNull(commandText, "commandText").strip();
        if (!legacy && commandText.isEmpty()) {
            throw new IllegalArgumentException("commandText is required");
        }
        status = Objects.requireNonNull(status, "status");
        stdout = Objects.requireNonNull(stdout, "stdout");
        stderr = Objects.requireNonNull(stderr, "stderr");
        if (receivedBytes < 0) {
            throw new IllegalArgumentException("receivedBytes must not be negative");
        }
        if (pageCount < 0) {
            throw new IllegalArgumentException("pageCount must not be negative");
        }
        parsedFacts = Map.copyOf(Objects.requireNonNull(parsedFacts, "parsedFacts"));
        parseWarnings = List.copyOf(Objects.requireNonNull(parseWarnings, "parseWarnings"));
    }

    public static CommandOutputBlock pending(CommandSpec command) {
        Objects.requireNonNull(command, "command");
        return new CommandOutputBlock(command.commandIndex(), command.commandText(), CommandBlockStatus.PENDING,
                "", "", 0, 0, false, null, null, Map.of(), List.of(), null, null, false);
    }

    public static CommandOutputBlock legacy(
            String stdout,
            String stderr,
            Integer exitCode,
            boolean truncated,
            Map<String, String> parsedFacts,
            String outcome) {
        return new CommandOutputBlock(1, "", CommandBlockStatus.SUCCEEDED,
                stdout, stderr, utf8Length(stdout) + utf8Length(stderr), 0, truncated,
                exitCode, outcome, parsedFacts, List.of(), null, null, true);
    }

    private static long utf8Length(String value) {
        return Objects.requireNonNull(value, "value").getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
