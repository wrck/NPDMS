package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.port.CollectionOutputEventQueryPort;
import com.dp.deviceops.core.port.CommandExecutionPort.OutputStreamType;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Objects;

/** JDBC replay adapter for the task-global collection output cursor. */
public final class JdbcCollectionOutputEventQueryAdapter implements CollectionOutputEventQueryPort {

    private final JdbcClient jdbc;

    public JdbcCollectionOutputEventQueryAdapter(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    public List<OutputEvent> findAfter(String namespace, String projectKey, String collectionId,
                                       long afterSequence, int limit) {
        String projectPredicate = hasText(projectKey) ? " and c.project_key = :projectKey" : "";
        JdbcClient.StatementSpec statement = jdbc.sql("""
                        select c.namespace, c.project_key, c.task_id,
                               e.target_id, e.sequence_no, e.command_index, e.stream_type, e.content,
                               e.received_bytes, e.page_count, e.output_truncated, e.created_at
                        from device_ops_collection_output_event e
                        join device_ops_collection_target t
                          on t.id = e.target_id and t.task_id = e.task_id
                        join device_ops_collection c on c.task_id = e.task_id
                        where c.namespace = :namespace
                          and c.task_id = :collectionId
                          and e.sequence_no > :afterSequence
                        """ + projectPredicate + """

                        order by e.sequence_no
                        limit :limit
                        """)
                .param("namespace", namespace)
                .param("collectionId", collectionId)
                .param("afterSequence", afterSequence)
                .param("limit", Math.max(1, Math.min(limit, 500)));
        if (hasText(projectKey)) {
            statement = statement.param("projectKey", projectKey);
        }
        return statement.query((resultSet, rowNumber) -> {
            if (!namespace.equals(resultSet.getString("namespace"))
                    || !collectionId.equals(resultSet.getString("task_id"))
                    || hasText(projectKey) && !projectKey.equals(resultSet.getString("project_key"))) {
                return null;
            }
            return new OutputEvent(
                        resultSet.getLong("target_id"),
                        resultSet.getLong("sequence_no"),
                        resultSet.getObject("command_index", Integer.class),
                        OutputStreamType.valueOf(resultSet.getString("stream_type")),
                        resultSet.getString("content"),
                        resultSet.getLong("received_bytes"),
                        resultSet.getInt("page_count"),
                        resultSet.getBoolean("output_truncated"),
                        resultSet.getTimestamp("created_at").toInstant());
        }).list().stream().filter(Objects::nonNull).toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
