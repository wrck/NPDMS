package com.dp.deviceops.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Non-sensitive trigger definition. It never contains connection data or credentials. */
public record InspectionSchedule(String namespace, String projectKey, String scheduleKey, String projectHint, List<String> deviceKeyHints,
                                 String scriptKey, String scriptVersion, String cron, String timezone, String callbackUri,
                                 boolean enabled, Instant nextRunAt, Instant lastRunAt, String lastStatus, long revision) {
    public InspectionSchedule {
        namespace = required(namespace, "namespace"); projectKey = required(projectKey, "projectKey"); scheduleKey = required(scheduleKey, "scheduleKey");
        projectHint = required(projectHint, "projectHint"); deviceKeyHints = List.copyOf(Objects.requireNonNull(deviceKeyHints, "deviceKeyHints"));
        if (deviceKeyHints.isEmpty() || deviceKeyHints.stream().anyMatch(value -> value == null || value.isBlank())) throw new IllegalArgumentException("deviceKeyHints required");
        scriptKey = required(scriptKey, "scriptKey"); scriptVersion = required(scriptVersion, "scriptVersion"); cron = required(cron, "cron"); timezone = required(timezone, "timezone"); callbackUri = required(callbackUri, "callbackUri");
        nextRunAt = Objects.requireNonNull(nextRunAt, "nextRunAt"); if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
    }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required"); return value; }
}
