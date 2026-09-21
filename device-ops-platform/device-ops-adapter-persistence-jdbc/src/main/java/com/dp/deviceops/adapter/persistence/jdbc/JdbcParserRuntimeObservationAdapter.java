package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.parser.runtime.model.ParseTaskState;
import com.dp.deviceops.parser.runtime.model.ParseWaitReason;
import com.dp.deviceops.parser.runtime.port.ParserRuntimeObservationPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class JdbcParserRuntimeObservationAdapter implements ParserRuntimeObservationPort {

    private final JdbcClient jdbc;

    public JdbcParserRuntimeObservationAdapter(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public RuntimeSnapshot snapshot(Instant now) {
        Map<ParseTaskState, Long> taskCounts = new EnumMap<>(ParseTaskState.class);
        for (ParseTaskState state : ParseTaskState.values()) {
            taskCounts.put(state, 0L);
        }
        jdbc.sql("select state,count(*) total from device_ops_parse_task group by state").query().listOfRows()
                .forEach(row -> taskCounts.put(ParseTaskState.valueOf((String) row.get("STATE")),
                        ((Number) row.get("TOTAL")).longValue()));
        Map<ParseWaitReason, Long> waitingCounts = new EnumMap<>(ParseWaitReason.class);
        for (ParseWaitReason reason : ParseWaitReason.values()) {
            waitingCounts.put(reason, 0L);
        }
        jdbc.sql("select wait_reason,count(*) total from device_ops_parse_task where state='WAITING' " +
                        "group by wait_reason").query().listOfRows().forEach(row -> waitingCounts.put(
                        ParseWaitReason.valueOf((String) row.get("WAIT_REASON")),
                        ((Number) row.get("TOTAL")).longValue()));
        Object oldestValue = jdbc.sql("select min(created_at) from device_ops_parse_task where state='QUEUED'")
                .query((resultSet, rowNumber) -> resultSet.getObject(1)).optional().orElse(null);
        long oldestSeconds = oldestValue == null ? 0
                : Math.max(0, java.time.Duration.between(ParserJdbcJson.instant(oldestValue), now).toSeconds());
        long deliveries = jdbc.sql("select count(*) from device_ops_outbox where event_type='PARSER_RESULT_READY' " +
                        "and delivered_at is null and dead_lettered_at is null")
                .query(Long.class).single();
        return new RuntimeSnapshot(taskCounts, waitingCounts, oldestSeconds, deliveries);
    }
}
