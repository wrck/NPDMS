package com.dp.deviceops.core.port;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parses one captured command output according to a script-provided configuration.
 */
public interface OutputParser {

    String type();

    ParseResult parse(String rawOutput, String configuration);

    /**
     * Framework-independent parsed values and non-fatal parse warnings.
     */
    record ParseResult(Map<String, String> values, List<String> warnings) {

        public ParseResult {
            values = Map.copyOf(Objects.requireNonNull(values, "values must not be null"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
        }
    }
}
