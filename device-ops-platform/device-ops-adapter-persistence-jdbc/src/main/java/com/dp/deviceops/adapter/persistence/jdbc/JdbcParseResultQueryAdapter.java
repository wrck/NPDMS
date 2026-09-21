package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.parser.runtime.model.ParseResultEnvelope;
import com.dp.deviceops.parser.runtime.model.ParseTask;
import com.dp.deviceops.parser.runtime.model.ParseTaskState;
import com.dp.deviceops.parser.runtime.model.ParseWaitReason;
import com.dp.deviceops.parser.runtime.port.ParseResultQueryPort;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.SemanticParseResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class JdbcParseResultQueryAdapter implements ParseResultQueryPort {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private final JdbcClient jdbc;
    private final ParserJdbcJson json;

    public JdbcParseResultQueryAdapter(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = new ParserJdbcJson(Objects.requireNonNull(objectMapper, "objectMapper"));
    }

    @Override
    public Optional<ParseTask> findTask(String callerNamespace, String taskId) {
        return jdbc.sql("select * from device_ops_parse_task where caller_namespace=:namespace and task_id=:task")
                .param("namespace", callerNamespace).param("task", taskId).query(this::mapTask).optional()
                .filter(task -> callerNamespace.equals(task.callerNamespace()));
    }

    @Override
    public List<ParseTask> listTasks(String callerNamespace, int limit, String afterTaskId) {
        String after = afterTaskId == null ? "" : afterTaskId;
        return jdbc.sql("select * from device_ops_parse_task where caller_namespace=:namespace " +
                        "and task_id>:after order by task_id limit :limit")
                .param("namespace", callerNamespace).param("after", after).param("limit", limit)
                .query(this::mapTask).list().stream()
                .filter(task -> callerNamespace.equals(task.callerNamespace())).toList();
    }

    @Override
    public List<ParseTaskResult> listTaskResultsByRequestPrefix(String callerNamespace, String requestPrefix) {
        return jdbc.sql("""
                select t.*,r.result_id as joined_result_id,r.release_id as result_release_id,
                       r.coordinate_json as result_coordinate_json,r.context_json as result_context_json,
                       r.source_result_id as result_source_result_id,r.created_at as result_created_at,
                       p.content as result_content
                from device_ops_parse_task t
                left join device_ops_parse_result r on r.task_id=t.task_id
                left join device_ops_parser_payload p on p.payload_id=r.structured_output_payload_id
                where t.caller_namespace=:namespace and t.request_id like :prefix
                order by t.request_id
                """)
                .param("namespace", callerNamespace).param("prefix", requestPrefix + "%")
                .query((resultSet, row) -> {
                    if (!callerNamespace.equals(resultSet.getString("caller_namespace"))) {
                        return null;
                    }
                    ParseTask task = mapTask(resultSet, row);
                    String resultId = resultSet.getString("joined_result_id");
                    ParseResultEnvelope result = resultId == null ? null : new ParseResultEnvelope(resultId,
                            task.taskId(), resultSet.getString("result_release_id"),
                            json.decode(resultSet.getString("result_coordinate_json"), ParserCoordinate.class),
                            json.decode(resultSet.getString("result_context_json"), MAP),
                            resultSet.getString("result_source_result_id"),
                            json.decode(resultSet.getString("result_content"), SemanticParseResult.class),
                            ParserJdbcJson.instant(resultSet.getObject("result_created_at")));
                    return new ParseTaskResult(task, result);
                }).list().stream().filter(Objects::nonNull).toList();
    }

    @Override
    public Optional<ParseResultEnvelope> findResult(String callerNamespace, String resultId) {
        return jdbc.sql("""
                select r.*,p.content,t.caller_namespace from device_ops_parse_result r
                join device_ops_parse_task t on t.task_id=r.task_id
                join device_ops_parser_payload p on p.payload_id=r.structured_output_payload_id
                where t.caller_namespace=:namespace and r.result_id=:result
                """).param("namespace", callerNamespace).param("result", resultId)
                .query((resultSet, row) -> {
                    if (!callerNamespace.equals(resultSet.getString("caller_namespace"))) {
                        return null;
                    }
                    return new ParseResultEnvelope(resultSet.getString("result_id"),
                            resultSet.getString("task_id"), resultSet.getString("release_id"),
                            json.decode(resultSet.getString("coordinate_json"), ParserCoordinate.class),
                            json.decode(resultSet.getString("context_json"), MAP), resultSet.getString("source_result_id"),
                            json.decode(resultSet.getString("content"), SemanticParseResult.class),
                            ParserJdbcJson.instant(resultSet.getObject("created_at")));
                }).optional();
    }

    private ParseTask mapTask(java.sql.ResultSet resultSet, int row) throws java.sql.SQLException {
        String destination = resultSet.getString("result_destination");
        String waitReason = resultSet.getString("wait_reason");
        return new ParseTask(resultSet.getString("task_id"), resultSet.getString("request_id"),
                resultSet.getString("caller_namespace"), resultSet.getString("log_type"),
                resultSet.getString("release_id"), new ParserCoordinate(resultSet.getString("log_type"),
                        resultSet.getString("release_version"), resultSet.getString("engine_version"),
                        resultSet.getString("rule_version"), resultSet.getString("projection_version"),
                        resultSet.getString("extension_id"), resultSet.getString("extension_version")),
                resultSet.getString("input_format"), resultSet.getString("input_payload_id"),
                json.decode(resultSet.getString("context_json"), MAP), resultSet.getString("source_result_id"),
                resultSet.getString("result_consumer_id"), destination == null ? null : URI.create(destination),
                ParseTaskState.valueOf(resultSet.getString("state")),
                waitReason == null ? null : ParseWaitReason.valueOf(waitReason), resultSet.getInt("attempt_count"),
                ParserJdbcJson.instant(resultSet.getObject("next_attempt_at")), resultSet.getString("lease_owner"),
                resultSet.getLong("lease_generation"), ParserJdbcJson.instant(resultSet.getObject("lease_expires_at")),
                resultSet.getString("result_id"), ParserJdbcJson.instant(resultSet.getObject("created_at")),
                ParserJdbcJson.instant(resultSet.getObject("updated_at")));
    }
}
