package com.dp.deviceops.core.service;

import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;

/** Recognizes the explicit command rejection observed on the integrated CLI, not arbitrary output substrings. */
final class CliCommandRejection {
    static CommandOutputBlock classify(CommandOutputBlock block) {
        if (block.legacy() || block.status() != CommandBlockStatus.SUCCEEDED) return block;
        String firstResponse = block.stdout().lines().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.equals(block.commandText()))
                .findFirst().orElse("");
        if (!firstResponse.matches("(?i)%\\s*Unknown command\\.?")) return block;
        return new CommandOutputBlock(block.commandIndex(), block.commandText(), CommandBlockStatus.FAILED,
                block.stdout(), block.stderr(), block.receivedBytes(), block.pageCount(), block.truncated(),
                block.exitCode(), "COMMAND_REJECTED", block.parsedFacts(), block.parseWarnings(),
                block.startedAt(), block.completedAt(), false);
    }
}
