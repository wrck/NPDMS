package com.dp.deviceops.parser.semantic.input;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.cli.CommandOutputBlockInput;
import com.dp.deviceops.parser.semantic.cli.SemanticParserInput;
import com.dp.deviceops.parser.semantic.evidence.EvidenceDocument;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

public final class CommandOutputBlockJsonInputAdapter implements ParserInputAdapter {

    private final ObjectMapper objectMapper;
    private final CommandOutputBlockEvidenceAdapter commandAdapter;

    public CommandOutputBlockJsonInputAdapter() {
        this(new CanonicalJson().mapper(), new CommandOutputBlockEvidenceAdapter());
    }

    public CommandOutputBlockJsonInputAdapter(
            ObjectMapper objectMapper,
            CommandOutputBlockEvidenceAdapter commandAdapter) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.commandAdapter = Objects.requireNonNull(commandAdapter, "commandAdapter");
    }

    @Override
    public String inputFormat() {
        return "command-output-block/v1";
    }

    @Override
    public EvidenceDocument decode(InputStream input) throws IOException {
        try {
            SemanticParserInput decoded = objectMapper.readValue(
                    Objects.requireNonNull(input, "input"), SemanticParserInput.class);
            if (decoded == null || !"1.0.0".equals(decoded.schemaVersion())
                    || decoded.commandBlocks() == null) {
                throw invalid("input schemaVersion or commandBlocks is invalid");
            }
            List<CommandOutputBlock> blocks = decoded.commandBlocks().stream()
                    .map(CommandOutputBlockInput::toDomain)
                    .toList();
            return commandAdapter.fromBlocks(blocks, decoded.contextSnapshot());
        } catch (JsonProcessingException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_INPUT,
                    "command output JSON is invalid", exception);
        } catch (SemanticParserError exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_INPUT,
                    "command block fields are invalid", exception);
        }
    }

    private static SemanticParserError invalid(String message) {
        return new SemanticParserError(SemanticParserError.INVALID_INPUT, message);
    }
}
