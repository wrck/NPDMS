package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.port.ManagementQueryPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Database-scoped allowlisted projections; never fetches output or credential columns. */
public final class JdbcManagementQueryAdapter implements ManagementQueryPort {
    private final JdbcClient jdbc;
    private final String binaryType;
    public JdbcManagementQueryAdapter(JdbcClient jdbc) { this(jdbc, false); }
    public JdbcManagementQueryAdapter(JdbcClient jdbc, boolean mysql) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.binaryType = mysql ? "BINARY" : "VARBINARY";
    }

    private static final String STATUS = """
        CASE
          WHEN EXISTS (SELECT 1 FROM device_ops_collection_target t WHERE t.task_id=c.task_id AND t.status='PARSING') THEN 'PARSING'
          WHEN EXISTS (SELECT 1 FROM device_ops_collection_target t WHERE t.task_id=c.task_id AND t.status='EXECUTING') THEN 'EXECUTING'
          WHEN EXISTS (SELECT 1 FROM device_ops_collection_target t WHERE t.task_id=c.task_id AND t.status='CONNECTING') THEN 'CONNECTING'
          WHEN EXISTS (SELECT 1 FROM device_ops_collection_target t WHERE t.task_id=c.task_id AND t.status='QUEUED') THEN 'QUEUED'
          WHEN NOT EXISTS (SELECT 1 FROM device_ops_collection_target t WHERE t.task_id=c.task_id) THEN 'UNKNOWN'
          WHEN NOT EXISTS (SELECT 1 FROM device_ops_collection_target t WHERE t.task_id=c.task_id AND t.status<>'SUCCEEDED') THEN 'SUCCEEDED'
          WHEN EXISTS (SELECT 1 FROM device_ops_collection_target t WHERE t.task_id=c.task_id AND t.status IN ('SUCCEEDED','PARTIAL_SUCCESS')) THEN 'PARTIAL_SUCCESS'
          WHEN NOT EXISTS (SELECT 1 FROM device_ops_collection_target t WHERE t.task_id=c.task_id AND t.status<>'CANCELLED') THEN 'CANCELLED'
          WHEN NOT EXISTS (SELECT 1 FROM device_ops_collection_target t WHERE t.task_id=c.task_id AND t.status<>'TIMED_OUT') THEN 'TIMED_OUT'
          ELSE 'FAILED' END
        """;

    @Override
    public Page<Summary> collections(Scope scope, Filter filter, int page, int size) {
        validatePage(page, size);
        var query = where(scope, filter);
        long total = count("FROM device_ops_collection c " + query.sql, query.params);
        String sql = "SELECT c.task_id,c.namespace,c.project_key,c.external_request_id,c.activity_type,c.created_at,"
                + "c.script_source,c.script_key,c.script_version,c.script_sha256," + STATUS + " AS aggregate_status,"
                + "(SELECT COUNT(*) FROM device_ops_collection_target t WHERE t.task_id=c.task_id) AS target_count "
                + "FROM device_ops_collection c " + query.sql + " ORDER BY c.created_at DESC,c.task_id DESC LIMIT :limit OFFSET :offset";
        var params = paged(query.params, page, size);
        var items = jdbc.sql(sql).params(params).query((rs, n) -> new Summary(rs.getString("task_id"),
                rs.getString("namespace"), rs.getString("project_key"), rs.getString("external_request_id"),
                rs.getString("activity_type"), "UNKNOWN".equals(rs.getString("aggregate_status")) ? null : CollectionStatus.valueOf(rs.getString("aggregate_status")),
                rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant(),
                rs.getLong("target_count"), rs.getString("script_source"), rs.getString("script_key"),
                rs.getString("script_version"), rs.getString("script_sha256"))).list();
        return new Page<>(items, total, page, size);
    }

    @Override
    public Overview overview(Scope scope, Filter filter) {
        var query = where(scope, filter);
        var counts = new LinkedHashMap<String, Long>();
        for (var status : CollectionStatus.values()) counts.put(status.name(), 0L);
        jdbc.sql("SELECT aggregate_status,COUNT(*) AS n FROM (SELECT " + STATUS + " AS aggregate_status FROM device_ops_collection c "
                + query.sql + ") visible GROUP BY aggregate_status").params(query.params)
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getLong(2))).list().forEach(e -> counts.put(e.getKey(), e.getValue()));
        return new Overview(counts.values().stream().mapToLong(Long::longValue).sum(), counts);
    }

    @Override
    public Page<ScriptVersion> scripts(Scope scope, Filter filter, int page, int size) {
        validatePage(page, size);
        var query = where(scope, filter);
        String relation = scriptJoin() + query.sql + " AND c.script_policy='REGISTER_VERSION'";
        String grouped = "SELECT s.namespace,s.script_key,s.source,v.version,v.sha256,v.parser_type,MAX(c.task_id) AS collection_id "
                + relation + " GROUP BY s.id,s.namespace,s.script_key,s.source,v.id,v.version,v.sha256,v.parser_type";
        long total = count("FROM (" + grouped + ") catalog", query.params);
        var items = jdbc.sql(grouped + " ORDER BY s.namespace,s.script_key,v.version DESC,s.source,v.id DESC LIMIT :limit OFFSET :offset")
                .params(paged(query.params, page, size)).query((rs, n) -> new ScriptVersion(rs.getString("namespace"),
                        rs.getString("script_key"), rs.getString("version"), rs.getString("source"), rs.getString("sha256"),
                        rs.getString("parser_type"), rs.getString("collection_id"), "LOCAL_MANAGED".equals(rs.getString("source")))).list();
        return new Page<>(items, total, page, size);
    }

    @Override
    public Optional<ScriptContent> content(Scope scope, String namespace, String collectionId) {
        var query = where(scope, new Filter(namespace, null, null, null, null, null));
        query.params.put("collectionId", collectionId);
        return jdbc.sql("SELECT s.namespace,s.script_key,v.version,s.source,v.sha256,v.content " + scriptJoin()
                        + query.sql + " AND " + exact("c.task_id", ":collectionId")
                        + " AND s.source='LOCAL_MANAGED' AND c.script_source='LOCAL_MANAGED' AND c.script_policy='REGISTER_VERSION'")
                .params(query.params).query((rs, n) -> new ScriptContent(rs.getString("namespace"), rs.getString("script_key"),
                        rs.getString("version"), rs.getString("source"), rs.getString("sha256"), rs.getString("content"))).optional();
    }

    private String scriptJoin() {
        return "FROM device_ops_collection c JOIN device_ops_script s ON " + exact("s.namespace", "c.namespace")
                + " AND " + exact("s.script_key", "c.script_key") + " AND " + exact("s.source", "c.script_source")
                + " JOIN device_ops_script_version v ON v.script_id=s.id AND " + exact("v.version", "c.script_version")
                + " AND " + exact("v.sha256", "c.script_sha256") + " ";
    }

    private Query where(Scope scope, Filter filter) {
        var params = new LinkedHashMap<String, Object>();
        var sql = new StringBuilder("WHERE ");
        sql.append(scope.allNamespaces() ? "1=1" : grants("c.namespace", "ns", scope.namespaces(), params));
        sql.append(" AND (c.project_key IS NULL OR ").append(grants("c.project_key", "pr", scope.projects(), params)).append(")");
        append(sql, params, "c.namespace", "namespace", filter.namespace());
        append(sql, params, "c.project_key", "project", filter.project());
        if (filter.device() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM device_ops_collection_target dt WHERE dt.task_id=c.task_id AND ")
                    .append(exact("dt.device_key", ":device")).append(")");
            params.put("device", filter.device());
        }
        if (filter.status() != null) {
            sql.append(" AND (").append(STATUS).append(")=:status");
            params.put("status", filter.status().name());
        }
        if (filter.from() != null) {
            sql.append(" AND c.created_at>=:fromTime");
            params.put("fromTime", Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND c.created_at<=:toTime");
            params.put("toTime", Timestamp.from(filter.to()));
        }
        return new Query(sql.toString(), params);
    }

    private String grants(String column, String prefix, List<String> grants, Map<String, Object> params) {
        if ("pr".equals(prefix) && grants.contains("*")) return "1=1";
        if (grants.isEmpty()) return "1=0";
        var terms = new ArrayList<String>();
        for (int i=0; i<grants.size(); i++) {
            String name = prefix + i;
            terms.add(exact(column, ":" + name));
            params.put(name, grants.get(i));
        }
        return "(" + String.join(" OR ", terms) + ")";
    }

    // The indexed equality narrows candidates; binary equality prevents collation-based grant widening.
    private String exact(String column, String value) {
        return "(" + column + "=" + value + " AND CAST(" + column + " AS " + binaryType + ")=CAST(" + value + " AS " + binaryType + "))";
    }
    private void append(StringBuilder sql, Map<String, Object> params, String column, String name, String value) {
        if (value != null) {
            sql.append(" AND ").append(exact(column, ":" + name));
            params.put(name, value);
        }
    }
    private long count(String relation, Map<String, Object> params) {
        return jdbc.sql("SELECT COUNT(*) " + relation).params(params).query(Long.class).single();
    }
    private static Map<String, Object> paged(Map<String, Object> original, int page, int size) {
        var params = new LinkedHashMap<>(original);
        params.put("limit", size);
        params.put("offset", (long) page * size);
        return params;
    }
    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be nonnegative and size must be 1..100");
    }
    private record Query(String sql, LinkedHashMap<String, Object> params) { }
}
