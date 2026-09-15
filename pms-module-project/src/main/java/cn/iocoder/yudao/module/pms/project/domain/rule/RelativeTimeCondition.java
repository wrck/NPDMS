package cn.iocoder.yudao.module.pms.project.domain.rule;

import tools.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;

/** Elapsed time, not a business calendar. Parsing and arithmetic belong to java.time. */
public final class RelativeTimeCondition {
    public static final String PREDICATE = "WAIT_ELAPSED";
    public enum Anchor { NODE_ACTIVATED, NODE_COMPLETED }

    private RelativeTimeCondition() { }

    public static Anchor anchor(JsonNode parameters) {
        return Anchor.valueOf(parameters.path("anchor").asText());
    }

    public static Duration duration(JsonNode parameters) {
        // https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Duration.html#parse(java.lang.CharSequence)
        Duration duration = Duration.parse(parameters.path("duration").asText());
        if (duration.isNegative()) throw new IllegalArgumentException("WAIT_DURATION_NEGATIVE");
        return duration;
    }

    public static Instant deadline(JsonNode parameters, Instant anchor) {
        return anchor.plus(duration(parameters));
    }

    public static RuleFact evaluate(JsonNode parameters, Instant anchor, Instant now) {
        if (anchor == null || now == null) return RuleFact.unknown("WAIT_ANCHOR_UNAVAILABLE");
        try {
            return RuleFact.known(!now.isBefore(deadline(parameters, anchor)));
        } catch (RuntimeException invalid) {
            return RuleFact.unknown("WAIT_DEFINITION_INVALID");
        }
    }
}
