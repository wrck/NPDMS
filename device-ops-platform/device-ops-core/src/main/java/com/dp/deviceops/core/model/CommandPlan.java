package com.dp.deviceops.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Protocol-neutral, ordered command lines frozen from one submitted script. */
public record CommandPlan(List<CommandSpec> commands) {

    public CommandPlan {
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("commands must not be empty");
        }
        for (int index = 0; index < commands.size(); index++) {
            CommandSpec command = Objects.requireNonNull(commands.get(index), "command");
            if (command.commandIndex() != index + 1) {
                throw new IllegalArgumentException("command indices must be contiguous and one-based");
            }
        }
    }

    public static CommandPlan fromScript(String script) {
        String normalized = Objects.requireNonNull(script, "script")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        List<CommandSpec> commands = new ArrayList<>();
        for (String line : normalized.split("\n", -1)) {
            String text = line.strip();
            if (!text.isEmpty()) {
                commands.add(new CommandSpec(commands.size() + 1, text));
            }
        }
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("script must contain a command");
        }
        return new CommandPlan(commands);
    }

    public record CommandSpec(int commandIndex, String commandText) {

        public CommandSpec {
            if (commandIndex < 1) {
                throw new IllegalArgumentException("commandIndex must be positive");
            }
            commandText = Objects.requireNonNull(commandText, "commandText").strip();
            if (commandText.isEmpty()) {
                throw new IllegalArgumentException("commandText is required");
            }
        }
    }
}
