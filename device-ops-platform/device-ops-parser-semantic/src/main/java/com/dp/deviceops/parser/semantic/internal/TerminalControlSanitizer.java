package com.dp.deviceops.parser.semantic.internal;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class TerminalControlSanitizer {

    private static final Pattern ANSI_CSI = Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");
    private static final Pattern ANSI_OSC = Pattern.compile("\\u001B\\][^\\u0007]*(?:\\u0007|\\u001B\\\\)");
    private static final Pattern BACKSPACE_OVERSTRIKE = Pattern.compile(".?\\u0008");

    public Result sanitize(List<NormalizedEvidenceUnit> units) {
        Set<Integer> changed = new HashSet<>();
        List<NormalizedEvidenceUnit> sanitized = units.stream().map(unit -> {
            List<String> content = sanitize(unit.contentLines(), unit.unitIndex(), changed);
            List<String> errors = sanitize(unit.errorLines(), unit.unitIndex(), changed);
            return new NormalizedEvidenceUnit(unit.unitIndex(), unit.unitType(), unit.attributes(),
                    content, errors, unit.truncated());
        }).toList();
        return new Result(sanitized, Set.copyOf(changed));
    }

    private static List<String> sanitize(List<String> lines, int index, Set<Integer> changed) {
        return lines.stream().map(line -> {
            String sanitized = ANSI_CSI.matcher(line).replaceAll("");
            sanitized = ANSI_OSC.matcher(sanitized).replaceAll("");
            sanitized = BACKSPACE_OVERSTRIKE.matcher(sanitized).replaceAll("");
            sanitized = sanitized.replace("\u0000", "");
            if (!sanitized.equals(line)) {
                changed.add(index);
            }
            return sanitized;
        }).toList();
    }

    public record Result(List<NormalizedEvidenceUnit> units, Set<Integer> changedUnitIndexes) {
    }
}
