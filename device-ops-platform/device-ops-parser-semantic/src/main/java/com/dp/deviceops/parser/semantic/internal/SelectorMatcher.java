package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.Selector;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledSelector;

import java.util.List;
import java.util.Locale;

final class SelectorMatcher {

    boolean matches(CompiledSelector selector, NormalizedEvidenceUnit unit) {
        return matches(selector, unit.commandText(), unit.contentLines());
    }

    boolean matches(CompiledSelector compiled, String commandText, List<String> contentLines) {
        Selector selector = compiled.source();
        String command = normalizeCommand(commandText);
        String content = String.join("\n", contentLines);
        return switch (selector.type()) {
            case "COMMAND_ALIAS" -> command.equals(normalizeCommand(selector.value()));
            case "COMMAND_REGEX" -> compiled.pattern().matcher(command).find();
            case "CONTENT_REGEX" -> compiled.pattern().matcher(content).find();
            case "TABLE_HEADERS" -> selector.values().stream()
                    .allMatch(header -> content.toLowerCase(Locale.ROOT)
                            .contains(header.toLowerCase(Locale.ROOT)));
            default -> false;
        };
    }

    private static String normalizeCommand(String command) {
        return command.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
