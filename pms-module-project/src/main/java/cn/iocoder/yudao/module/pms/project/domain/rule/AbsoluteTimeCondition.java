package cn.iocoder.yudao.module.pms.project.domain.rule;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.OffsetDateTime;

/** An absolute instant, never a server-local date or a second boolean interpreter. */
public final class AbsoluteTimeCondition {
    public static final String PREDICATE = "TIME_REACHED";
    private AbsoluteTimeCondition() { }

    // JDK 25: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/OffsetDateTime.html#parse(java.lang.CharSequence)
    public static Instant deadline(JsonNode parameters) {
        return OffsetDateTime.parse(parameters.path("at").asText()).toInstant();
    }

    public static RuleFact evaluate(JsonNode parameters, Instant now) {
        if (now == null) return RuleFact.unknown("TIME_CONTEXT_UNAVAILABLE");
        try {
            return RuleFact.known(!now.isBefore(deadline(parameters)));
        } catch (RuntimeException invalid) {
            return RuleFact.unknown("TIME_DEFINITION_INVALID");
        }
    }
}
