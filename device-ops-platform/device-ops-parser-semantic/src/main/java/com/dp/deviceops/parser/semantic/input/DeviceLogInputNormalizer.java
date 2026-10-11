package com.dp.deviceops.parser.semantic.input;

import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.parser.semantic.cli.CommandOutputBlockInput;
import com.dp.deviceops.parser.semantic.cli.SemanticParserInput;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.dp.deviceops.parser.semantic.internal.SensitiveValueRedactor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Adapts offline evidence to the existing published command-output parser, without executing commands. */
public final class DeviceLogInputNormalizer {
    public static final String INPUT_FORMAT = "command-output-block/v1";
    private static final int MAX_BLOCKS = 1024;
    private static final Pattern COMMAND = Pattern.compile(
            "^(?:(?:[^\\s<>\\[\\]]{1,128}[#>$]|<[^>]{1,128}>|\\[[^\\]]{1,128}\\])\\s*)?"
                    + "((?:show|display)\\s+\\S[^\\r\\n]{0,255})\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN = Pattern.compile(
            "(?im)(\\b(?:access[_-]?token|refresh[_-]?token|token|api[_-]?key|authorization)\\b"
                    + "[\\t ]*(?:[:=][\\t ]*|[\\t ]+)(?:Bearer[\\t ]+)?)(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\\s,;]+)");
    private final ObjectMapper json = new CanonicalJson().mapper();
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)(\"(?:password|passphrase|secret|community|private[_ -]?key|credential|access[_-]?token|refresh[_-]?token|token|api[_-]?key|authorization)\"[\\t ]*:[\\t ]*)"
                    + "\"(?:\\\\.|[^\"\\\\])*\"");
    private final SensitiveValueRedactor redactor = new SensitiveValueRedactor(true);

    public Normalized normalize(String content, String commandHint) {
        if (content == null || content.isBlank() || content.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("LOG_TEXT_REQUIRED");
        }
        String hint = commandHint == null ? "" : commandHint.strip();
        if (hint.length() > 256 || hint.contains("\n") || hint.contains("\r")) {
            throw new IllegalArgumentException("COMMAND_HINT_INVALID");
        }
        String text = content.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        List<CommandOutputBlock> blocks = jsonBlocks(text);
        List<String> warnings = new ArrayList<>();
        warnings.add("UPLOADED_LOG_EXECUTION_NOT_VERIFIED");
        if (blocks == null) {
            blocks = textBlocks(text, hint);
            if (blocks.stream().anyMatch(block -> block.commandText().isBlank())) {
                warnings.add("COMMAND_NOT_IDENTIFIED");
            }
        }
        return sanitize(blocks, warnings);
    }

    public Normalized fromCollectedBlocks(List<CommandOutputBlock> blocks) {
        return sanitize(blocks, new ArrayList<>());
    }

    private Normalized sanitize(List<CommandOutputBlock> blocks, List<String> warnings) {
        if (blocks == null || blocks.isEmpty() || blocks.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("LOG_BLOCK_COUNT_INVALID");
        }
        List<CommandOutputBlockInput> safe = new ArrayList<>();
        boolean redacted = false;
        for (CommandOutputBlock block : blocks) {
            String command = redact(block.commandText());
            String stdout = redact(block.stdout());
            String stderr = redact(block.stderr());
            redacted |= !command.equals(block.commandText()) || !stdout.equals(block.stdout())
                    || !stderr.equals(block.stderr());
            // Execution metadata is retained for collected evidence. Uploaded evidence is explicitly unverified.
            safe.add(new CommandOutputBlockInput(block.commandIndex(), command, block.status(), stdout, stderr,
                    null, block.pageCount(), block.truncated(), block.exitCode(), null, Map.of(), List.of(),
                    block.startedAt(), block.completedAt(), block.legacy()));
        }
        if (redacted) warnings.add("SENSITIVE_VALUES_REDACTED");
        return new Normalized(new SemanticParserInput("1.0.0", null, safe), List.copyOf(warnings));
    }

    private String redact(String value) {
        String safe = JSON_SECRET.matcher(value == null ? "" : value).replaceAll("$1\"[REDACTED]\"");
        return TOKEN.matcher(redactor.redactText(safe)).replaceAll("$1[REDACTED]");
    }

    private List<CommandOutputBlock> jsonBlocks(String text) {
        if (!text.stripLeading().startsWith("{")) return null;
        try {
            JsonNode root = json.readTree(text);
            if (!root.has("commandBlocks")) return null;
            SemanticParserInput input = json.treeToValue(root, SemanticParserInput.class);
            if (!"1.0.0".equals(input.schemaVersion()) || input.commandBlocks() == null) {
                throw new IllegalArgumentException("LOG_JSON_INVALID");
            }
            List<CommandOutputBlock> blocks = input.commandBlocks().stream()
                    .map(CommandOutputBlockInput::toDomain).toList();
            // Reuse strict uniqueness / index validation before persisting the normalized input.
            new CommandOutputBlockEvidenceAdapter().fromBlocks(blocks);
            return blocks;
        } catch (IOException | RuntimeException error) {
            throw new IllegalArgumentException("LOG_JSON_INVALID");
        }
    }

    private List<CommandOutputBlock> textBlocks(String text, String hint) {
        List<CommandOutputBlock> blocks = new ArrayList<>();
        String command = hint;
        List<String> output = new ArrayList<>();
        boolean headerSeen = false;
        for (String line : text.split("\n", -1)) {
            var match = COMMAND.matcher(line.strip());
            if (match.matches()) {
                String nextCommand = match.group(1).strip();
                if (!String.join("\n", output).isBlank() || (headerSeen && !command.equalsIgnoreCase(nextCommand))) {
                    append(blocks, command, String.join("\n", output));
                }
                command = nextCommand;
                output.clear();
                headerSeen = true;
            } else {
                output.add(line);
            }
        }
        if (!String.join("\n", output).isBlank() || headerSeen) append(blocks, command, String.join("\n", output));
        return blocks;
    }

    private void append(List<CommandOutputBlock> blocks, String command, String text) {
        if (blocks.size() >= MAX_BLOCKS) throw new IllegalArgumentException("LOG_BLOCK_COUNT_INVALID");
        // SUCCEEDED is the existing adapter's readable-block discriminator, not a recorded device execution.
        // No CollectionTask is created; the parse context always labels uploaded execution as unverified.
        blocks.add(new CommandOutputBlockInput(blocks.size() + 1, command, CommandBlockStatus.SUCCEEDED,
                text, "", null, 0, false, null, null, Map.of(), List.of(), null, null, true).toDomain());
    }

    public record Normalized(SemanticParserInput input, List<String> warnings) { }
}
