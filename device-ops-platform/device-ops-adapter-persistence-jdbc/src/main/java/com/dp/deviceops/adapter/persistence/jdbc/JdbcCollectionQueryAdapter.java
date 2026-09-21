package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.model.CollectionTarget;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.CollectionQueryPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.Instant;

/** JDBC projection for immutable collection evidence. */
public final class JdbcCollectionQueryAdapter implements CollectionQueryPort {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcCollectionQueryAdapter(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.json = Objects.requireNonNull(json, "json must not be null");
    }

    @Override
    public Optional<CollectionDetails> find(String namespace, String collectionId) {
        return findInternal(namespace, Optional.empty(), collectionId);
    }

    @Override
    public Optional<CollectionDetails> find(String namespace, String projectKey, String collectionId) {
        return findInternal(namespace, Optional.of(projectKey), collectionId);
    }

    private Optional<CollectionDetails> findInternal(String namespace, Optional<String> projectKey,
                                                     String collectionId) {
        String projectPredicate = projectKey.isPresent() ? " AND project_key = :projectKey" : "";
        JdbcClient.StatementSpec statement = jdbc.sql("""
                        SELECT task_id, namespace, project_key, external_request_id, activity_type,
                               script_source, script_key, script_version, script_sha256, parser_type
                        FROM device_ops_collection
                        WHERE namespace = :namespace AND task_id = :collectionId
                        """ + projectPredicate)
                .param("namespace", namespace)
                .param("collectionId", collectionId);
        if (projectKey.isPresent()) {
            statement = statement.param("projectKey", projectKey.orElseThrow());
        }
        Optional<Header> header = statement
                .query((resultSet, rowNumber) -> new Header(
                        resultSet.getString("task_id"),
                        resultSet.getString("namespace"),
                        resultSet.getString("project_key"),
                        resultSet.getString("external_request_id"),
                        resultSet.getString("activity_type"),
                        resultSet.getString("script_source"),
                        resultSet.getString("script_key"),
                        resultSet.getString("script_version"),
                        resultSet.getString("script_sha256"),
                        resultSet.getString("parser_type")
                ))
                .optional()
                .filter(value -> namespace.equals(value.namespace()) && collectionId.equals(value.collectionId())
                        && projectKey.map(project -> project.equals(value.projectKey())).orElse(true));
        return header.map(value -> {
            List<TargetDetails> targets = findTargets(collectionId);
            return new CollectionDetails(
                    value.collectionId(), value.namespace(), value.projectKey(), value.externalRequestId(),
                    value.activityType(), aggregate(targets),
                    new ScriptIdentity(value.scriptSource(), value.scriptKey(), value.scriptVersion(),
                            value.scriptSha256(), value.parserType()),
                    targets
            );
        });
    }

    private List<TargetDetails> findTargets(String collectionId) {
        return jdbc.sql("""
                        SELECT t.id, c.namespace, c.project_key, t.project_name, t.project_code, t.device_key,
                               t.device_name, t.vendor, t.model, t.extensions_json, t.protocol, t.telnet_prompts_json,
                               t.host, t.port, t.username,
                               t.host_key_fingerprint, t.status, t.standard_output, t.standard_error, t.exit_code,
                               t.output_truncated, t.parsed_facts_json, t.outcome_message
                        FROM device_ops_collection_target t
                        JOIN device_ops_collection c ON c.task_id = t.task_id
                        WHERE t.task_id = :collectionId
                        ORDER BY t.id
                        """)
                .param("collectionId", collectionId)
                .query((resultSet, rowNumber) -> {
                    long targetId = resultSet.getLong("id");
                    String stdout = resultSet.getString("standard_output");
                    String stderr = resultSet.getString("standard_error");
                    Integer exitCode = resultSet.getObject("exit_code", Integer.class);
                    boolean truncated = resultSet.getBoolean("output_truncated");
                    Map<String, String> facts = readMap(resultSet.getString("parsed_facts_json"));
                    String outcome = resultSet.getString("outcome_message");
                    List<CommandOutputBlock> blocks = findCommandBlocks(targetId);
                    if (blocks.isEmpty() && (hasText(stdout) || hasText(stderr) || exitCode != null
                            || truncated || !facts.isEmpty() || hasText(outcome))) {
                        blocks = List.of(CommandOutputBlock.legacy(
                                Objects.toString(stdout, ""), Objects.toString(stderr, ""),
                                exitCode, truncated, facts, outcome));
                    }
                    return new TargetDetails(
                        targetId,
                        new ContextSnapshot(
                                resultSet.getString("project_key") == null ? null : new ProjectSnapshot(
                                        resultSet.getString("namespace"),
                                        resultSet.getString("project_key"),
                                        resultSet.getString("project_name"),
                                        resultSet.getString("project_code")
                                ),
                                resultSet.getString("device_key") == null ? null : new DeviceSnapshot(
                                        resultSet.getString("device_key"),
                                        resultSet.getString("device_name"),
                                        resultSet.getString("vendor"),
                                        resultSet.getString("model")
                                ),
                                readMap(resultSet.getString("extensions_json"))
                        ),
                        new CollectionTarget.EndpointSnapshot(
                                ConnectionProtocol.valueOf(resultSet.getString("protocol")),
                                resultSet.getString("host"),
                                resultSet.getInt("port"),
                                resultSet.getString("username"),
                                resultSet.getString("host_key_fingerprint"),
                                readPrompts(resultSet.getString("telnet_prompts_json"))
                        ),
                        CollectionStatus.valueOf(resultSet.getString("status")),
                        stdout,
                        stderr,
                        exitCode,
                        truncated,
                        facts,
                        outcome,
                        blocks
                    );
                })
                .list();
    }

    private List<CommandOutputBlock> findCommandBlocks(long targetId) {
        return jdbc.sql("""
                        select command_index, command_text, status, standard_output, standard_error,
                               received_bytes, page_count, output_truncated, exit_code, outcome_message,
                               parsed_facts_json, parse_warnings_json, started_at, completed_at, legacy_record
                        from device_ops_collection_command_output
                        where target_id=:targetId
                        order by command_index
                        """)
                .param("targetId", targetId)
                .query((resultSet, rowNumber) -> new CommandOutputBlock(
                        resultSet.getInt("command_index"),
                        resultSet.getString("command_text"),
                        CommandBlockStatus.valueOf(resultSet.getString("status")),
                        resultSet.getString("standard_output"),
                        resultSet.getString("standard_error"),
                        resultSet.getLong("received_bytes"),
                        resultSet.getInt("page_count"),
                        resultSet.getBoolean("output_truncated"),
                        resultSet.getObject("exit_code", Integer.class),
                        resultSet.getString("outcome_message"),
                        readMap(resultSet.getString("parsed_facts_json")),
                        readStringList(resultSet.getString("parse_warnings_json")),
                        instant(resultSet.getTimestamp("started_at")),
                        instant(resultSet.getTimestamp("completed_at")),
                        resultSet.getBoolean("legacy_record")))
                .list();
    }

    private List<String> readStringList(String value) {
        if (!hasText(value)) return List.of();
        try {
            return json.readValue(value, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("persisted collection warnings are invalid", exception);
        }
    }

    private static Instant instant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private CommandExecutionPort.TelnetPrompts readPrompts(String value) {
        if (value == null) {
            return null;
        }
        try {
            return json.readValue(value, CommandExecutionPort.TelnetPrompts.class);
        } catch (Exception exception) {
            throw new IllegalStateException("persisted Telnet prompts are invalid", exception);
        }
    }

    private Map<String, String> readMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(value, STRING_MAP);
        } catch (Exception exception) {
            throw new IllegalStateException("persisted collection facts are invalid", exception);
        }
    }

    private static CollectionStatus aggregate(List<TargetDetails> targets) {
        if (targets.isEmpty()) {
            throw new IllegalStateException("collection has no targets");
        }
        boolean allTerminal = targets.stream().allMatch(target -> terminal(target.status()));
        if (!allTerminal) {
            return targets.stream().map(TargetDetails::status).filter(status -> !terminal(status))
                    .max(Comparator.comparingInt(JdbcCollectionQueryAdapter::phase))
                    .orElse(CollectionStatus.QUEUED);
        }
        if (targets.stream().allMatch(target -> target.status() == CollectionStatus.SUCCEEDED)) {
            return CollectionStatus.SUCCEEDED;
        }
        if (targets.stream().anyMatch(target -> target.status() == CollectionStatus.SUCCEEDED
                || target.status() == CollectionStatus.PARTIAL_SUCCESS)) {
            return CollectionStatus.PARTIAL_SUCCESS;
        }
        if (targets.stream().allMatch(target -> target.status() == CollectionStatus.CANCELLED)) {
            return CollectionStatus.CANCELLED;
        }
        if (targets.stream().allMatch(target -> target.status() == CollectionStatus.TIMED_OUT)) {
            return CollectionStatus.TIMED_OUT;
        }
        return CollectionStatus.FAILED;
    }

    private static int phase(CollectionStatus status) {
        return switch (status) {
            case QUEUED -> 0;
            case CONNECTING -> 1;
            case EXECUTING -> 2;
            case PARSING -> 3;
            default -> 4;
        };
    }

    private static boolean terminal(CollectionStatus status) {
        return phase(status) == 4;
    }

    private record Header(String collectionId, String namespace, String projectKey, String externalRequestId,
                          String activityType, String scriptSource, String scriptKey, String scriptVersion,
                          String scriptSha256, String parserType) {
    }
}
