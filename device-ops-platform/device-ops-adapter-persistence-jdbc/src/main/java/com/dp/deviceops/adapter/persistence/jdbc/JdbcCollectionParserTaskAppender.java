package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository;
import com.dp.deviceops.parser.runtime.port.ParserPayloadStore;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.cli.CommandOutputBlockInput;
import com.dp.deviceops.parser.semantic.cli.SemanticParserInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Adds the pinned aggregate parser task inside the collection terminal transaction. */
public final class JdbcCollectionParserTaskAppender {

    private static final String MEDIA_TYPE = "application/json";
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final ParserPayloadStore payloads;
    private final ParseTaskRepository tasks;
    private final Clock clock;

    public JdbcCollectionParserTaskAppender(JdbcClient jdbc, ObjectMapper json,
            ParserPayloadStore payloads, ParseTaskRepository tasks, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = Objects.requireNonNull(json, "json");
        this.payloads = Objects.requireNonNull(payloads, "payloads");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void append(long targetId, CollectionStatus terminalStatus) {
        if (terminalStatus == CollectionStatus.CANCELLED) return;
        List<Map<String, Object>> requests = jdbc.sql("""
                select c.task_id,c.namespace,c.project_key,c.external_request_id,c.activity_type,
                       t.device_key,t.vendor,t.model,
                       p.log_type,p.release_id,p.input_format,p.result_consumer_id,p.result_destination,
                       r.coordinate_json
                from device_ops_collection_target t
                join device_ops_collection c on c.task_id=t.task_id
                join device_ops_collection_parser_request p on p.task_id=c.task_id
                join device_ops_parser_release r on r.release_id=p.release_id
                where t.id=:target
                """).param("target", targetId).query().listOfRows();
        Map<String, Object> request = requests.isEmpty() ? null : requests.getFirst();
        if (request == null) return;

        String collectionId = (String) request.get("TASK_ID");
        String namespace = (String) request.get("NAMESPACE");
        String requestId = "collection:" + collectionId + ":target:" + targetId;
        boolean exists = jdbc.sql("select count(*) from device_ops_parse_task "
                        + "where caller_namespace=:namespace and request_id=:request")
                .param("namespace", namespace).param("request", requestId)
                .query(Integer.class).single() > 0;
        if (exists) return;

        List<CommandOutputBlockInput> blocks = jdbc.sql("""
                select * from device_ops_collection_command_output
                where target_id=:target order by command_index
                """).param("target", targetId).query((rs, row) -> new CommandOutputBlockInput(
                        rs.getInt("command_index"), rs.getString("command_text"),
                        CommandBlockStatus.valueOf(rs.getString("status")),
                        rs.getString("standard_output"), rs.getString("standard_error"),
                        rs.getLong("received_bytes"), rs.getInt("page_count"),
                        rs.getBoolean("output_truncated"), rs.getObject("exit_code", Integer.class),
                        rs.getString("outcome_message"), stringMap(rs.getString("parsed_facts_json")),
                        stringList(rs.getString("parse_warnings_json")), instant(rs.getTimestamp("started_at")),
                        instant(rs.getTimestamp("completed_at")), rs.getBoolean("legacy_record"))).list();

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("collectionId", collectionId);
        context.put("targetId", targetId);
        putIfPresent(context, "projectKey", request.get("PROJECT_KEY"));
        putIfPresent(context, "deviceKey", request.get("DEVICE_KEY"));
        putIfPresent(context, "deviceVendor", request.get("VENDOR"));
        putIfPresent(context, "deviceModel", request.get("MODEL"));
        putIfPresent(context, "externalRequestId", request.get("EXTERNAL_REQUEST_ID"));
        putIfPresent(context, "activityType", request.get("ACTIVITY_TYPE"));

        String payloadId;
        try {
            byte[] content = json.writeValueAsBytes(
                    new SemanticParserInput("1.0.0", collectionId, context, blocks));
            payloadId = payloads.put(MEDIA_TYPE, new ByteArrayInputStream(content));
        } catch (IOException exception) {
            throw new IllegalStateException("collection parser payload cannot be persisted", exception);
        }

        ParserCoordinate coordinate = decodeCoordinate((String) request.get("COORDINATE_JSON"));
        String destination = (String) request.get("RESULT_DESTINATION");
        tasks.submit(new ParseTaskRepository.SubmitRequest(UUID.randomUUID().toString(), requestId,
                namespace, (String) request.get("LOG_TYPE"), (String) request.get("RELEASE_ID"), coordinate,
                (String) request.get("INPUT_FORMAT"), payloadId, context, null,
                (String) request.get("RESULT_CONSUMER_ID"),
                destination == null ? null : URI.create(destination), clock.instant(),
                (String) request.get("RELEASE_ID"), (String) request.get("EXTERNAL_REQUEST_ID"),
                (String) request.get("ACTIVITY_TYPE")));
    }

    private ParserCoordinate decodeCoordinate(String value) {
        try {
            return json.readValue(value, ParserCoordinate.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("frozen parser coordinate cannot be decoded", exception);
        }
    }

    private Map<String, String> stringMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return json.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("command facts cannot be decoded", exception);
        }
    }

    private List<String> stringList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return json.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("command warnings cannot be decoded", exception);
        }
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }
}
