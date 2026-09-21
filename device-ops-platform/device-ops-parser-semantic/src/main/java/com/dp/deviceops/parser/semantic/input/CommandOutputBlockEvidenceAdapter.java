package com.dp.deviceops.parser.semantic.input;

import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.evidence.EvidenceDocument;
import com.dp.deviceops.parser.semantic.evidence.EvidenceUnit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CommandOutputBlockEvidenceAdapter {

    public EvidenceDocument fromBlocks(List<CommandOutputBlock> commandBlocks) {
        return fromBlocks(commandBlocks, Map.of());
    }

    public EvidenceDocument fromBlocks(
            List<CommandOutputBlock> commandBlocks,
            Map<String, Object> contextSnapshot) {
        if (commandBlocks == null) {
            throw invalid("commandBlocks must not be null");
        }
        List<CommandOutputBlock> sorted;
        try {
            sorted = commandBlocks.stream()
                    .map(block -> Objects.requireNonNull(block, "command block"))
                    .sorted(Comparator.comparingInt(CommandOutputBlock::commandIndex))
                    .toList();
        } catch (NullPointerException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_INPUT,
                    "commandBlocks must not contain null", exception);
        }
        Set<Integer> indexes = new HashSet<>();
        List<EvidenceUnit> units = new ArrayList<>(sorted.size());
        for (CommandOutputBlock block : sorted) {
            if (!indexes.add(block.commandIndex())) {
                throw invalid("commandIndex must be unique");
            }
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("commandText", block.commandText());
            attributes.put("status", block.status().name());
            attributes.put("pageCount", Integer.toString(block.pageCount()));
            attributes.put("exitCode", Objects.toString(block.exitCode(), ""));
            units.add(new EvidenceUnit(block.commandIndex(), "COMMAND_OUTPUT", attributes,
                    lines(block.stdout()), lines(block.stderr()), block.truncated()));
        }
        return new EvidenceDocument(units, contextSnapshot);
    }

    private static List<String> lines(String text) {
        return List.of(normalizeLineEndings(text).split("\\n", -1));
    }

    private static String normalizeLineEndings(String text) {
        return Objects.requireNonNull(text, "text").replace("\r\n", "\n").replace('\r', '\n');
    }

    private static SemanticParserError invalid(String message) {
        return new SemanticParserError(SemanticParserError.INVALID_INPUT, message);
    }
}
