package com.dp.deviceops.server;

import com.dp.deviceops.core.port.OutputParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
public class OutputParserConfiguration {
    @Bean OutputParser noneOutputParser() {
        return parser("NONE", (raw, configuration) -> new OutputParser.ParseResult(Map.of(), List.of()));
    }

    @Bean OutputParser jsonOutputParser(ObjectMapper json) {
        return parser("JSON", (raw, configuration) -> {
            try {
                JsonNode root = json.readTree(raw);
                if (root == null || !root.isObject()) throw new IllegalArgumentException("JSON output must be an object");
                Map<String, String> values = new LinkedHashMap<>();
                root.fields().forEachRemaining(entry -> values.put(entry.getKey(),
                        entry.getValue().isValueNode() ? entry.getValue().asText() : entry.getValue().toString()));
                return new OutputParser.ParseResult(values, List.of());
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalArgumentException("JSON output is invalid", exception);
            }
        });
    }

    @Bean OutputParser keyValueOutputParser(ObjectMapper json) {
        return parser("KEY_VALUE", (raw, configuration) -> {
            KeyValueOptions options = options(json, configuration);
            Map<String, String> values = new LinkedHashMap<>();
            List<String> warnings = new ArrayList<>();
            for (String line : raw.split("\\R", -1)) {
                if (line.isBlank() && options.ignoreBlankLines()) continue;
                int split = line.indexOf(options.separator());
                if (split < 1) {
                    warnings.add("ignored line without separator");
                    continue;
                }
                String key = line.substring(0, split).trim();
                String previous = values.put(key, line.substring(split + options.separator().length()).trim());
                if (previous != null) warnings.add("duplicate key replaced: " + key);
            }
            return new OutputParser.ParseResult(values, warnings);
        });
    }

    private static KeyValueOptions options(ObjectMapper json, String configuration) {
        if (configuration == null || configuration.isBlank()) return new KeyValueOptions("=", true);
        try {
            KeyValueOptions value = json.readValue(configuration, KeyValueOptions.class);
            if (value.separator() == null || value.separator().isEmpty()) throw new IllegalArgumentException("separator is required");
            return value;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("KEY_VALUE parser configuration is invalid", exception);
        }
    }

    private static OutputParser parser(String type, ParserBody body) {
        return new OutputParser() {
            public String type() { return type; }
            public ParseResult parse(String rawOutput, String configuration) {
                return body.parse(Objects.requireNonNull(rawOutput, "rawOutput"), configuration);
            }
        };
    }
    private interface ParserBody { OutputParser.ParseResult parse(String raw, String configuration); }
    private record KeyValueOptions(String separator, boolean ignoreBlankLines) { }
}
