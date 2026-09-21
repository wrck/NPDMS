package com.dp.deviceops.parser.semantic.internal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SensitiveEvidenceSanitizer {

    private final SensitiveValueRedactor redactor;
    private final boolean enhanced;

    public SensitiveEvidenceSanitizer(SensitiveValueRedactor redactor) {
        this(redactor, false);
    }

    public SensitiveEvidenceSanitizer(SensitiveValueRedactor redactor, boolean enhanced) {
        Objects.requireNonNull(redactor, "redactor");
        this.redactor = enhanced ? new SensitiveValueRedactor(true) : redactor;
        this.enhanced = enhanced;
    }

    public Result sanitize(List<NormalizedEvidenceUnit> units) {
        Objects.requireNonNull(units, "units");
        int redactedValueCount = 0;
        Set<Integer> changedCommandIndexes = new LinkedHashSet<>();
        List<NormalizedEvidenceUnit> safeUnits = new ArrayList<>(units.size());
        for (NormalizedEvidenceUnit unit : units) {
            SanitizedLines content = sanitizeLines(unit.contentLines());
            SanitizedLines errors = sanitizeLines(unit.errorLines());
            int unitCount = Math.addExact(content.redactedValueCount(), errors.redactedValueCount());
            redactedValueCount = Math.addExact(redactedValueCount, unitCount);
            if (unitCount > 0) {
                changedCommandIndexes.add(unit.provenance().commandIndex());
            }
            safeUnits.add(new NormalizedEvidenceUnit(unit.unitIndex(), unit.unitType(), unit.attributes(),
                    content.lines(), errors.lines(), unit.truncated(), unit.provenance()));
        }
        return new Result(List.copyOf(safeUnits), redactedValueCount, Set.copyOf(changedCommandIndexes));
    }

    private SanitizedLines sanitizeLines(List<String> lines) {
        if (enhanced) {
            SensitiveValueRedactor.RedactedLines sanitized = redactor.redactLinesWithCount(lines);
            return new SanitizedLines(sanitized.lines(), sanitized.redactedValueCount());
        }
        List<String> safeLines = new ArrayList<>(lines.size());
        int count = 0;
        for (String line : lines) {
            SensitiveValueRedactor.RedactedText sanitized = redactor.redactTextWithCount(line);
            safeLines.add(sanitized.value());
            count = Math.addExact(count, sanitized.redactedValueCount());
        }
        return new SanitizedLines(List.copyOf(safeLines), count);
    }

    public record Result(
            List<NormalizedEvidenceUnit> units,
            int redactedValueCount,
            Set<Integer> changedCommandIndexes) {
        public Result {
            units = List.copyOf(Objects.requireNonNull(units, "units"));
            if (redactedValueCount < 0) {
                throw new IllegalArgumentException("redactedValueCount must not be negative");
            }
            changedCommandIndexes = Set.copyOf(
                    Objects.requireNonNull(changedCommandIndexes, "changedCommandIndexes"));
        }
    }

    private record SanitizedLines(List<String> lines, int redactedValueCount) {
    }
}
