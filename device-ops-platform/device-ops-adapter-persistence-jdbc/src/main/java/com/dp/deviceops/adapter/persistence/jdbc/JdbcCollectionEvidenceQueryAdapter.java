package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.port.CollectionEvidenceQueryPort;
import com.dp.deviceops.core.port.ManagementQueryPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.util.*;

/** Task-owned input only; SQL authorization runs before any evidence is materialized. */
public final class JdbcCollectionEvidenceQueryAdapter implements CollectionEvidenceQueryPort {
    private final JdbcClient jdbc;
    private final String binaryType;
    private static final ObjectMapper JSON = new ObjectMapper();
    public JdbcCollectionEvidenceQueryAdapter(JdbcClient jdbc) { this(jdbc, false); }
    public JdbcCollectionEvidenceQueryAdapter(JdbcClient jdbc, boolean mysql) {
        this.jdbc = Objects.requireNonNull(jdbc);
        binaryType = mysql ? "BINARY" : "VARBINARY";
    }
    @Override public Optional<Evidence> find(ManagementQueryPort.Scope scope, String namespace, String projectKey, String id) {
        Objects.requireNonNull(namespace, "namespace");
        var params = new LinkedHashMap<String, Object>();
        params.put("namespace", namespace);
        params.put("id", id);
        String where = " WHERE " + exact("c.namespace", ":namespace") + " AND " + exact("c.task_id", ":id")
                + " AND " + (scope.allNamespaces() ? "1=1" : grants("c.namespace", "ns", scope.namespaces(), params))
                + " AND (c.project_key IS NULL OR " + (scope.projects().contains("*") ? "1=1" : grants("c.project_key", "pr", scope.projects(), params)) + ")";
        if (projectKey != null) { where += " AND " + exact("c.project_key", ":project"); params.put("project", projectKey); }
        return jdbc.sql("SELECT c.task_id,c.namespace,c.project_key,c.external_request_id,c.activity_type,c.created_at,"
                + "c.script_source,c.script_key,c.script_version,c.script_policy,c.parser_type,c.script_sha256,c.script_content,c.submission_snapshot_json FROM device_ops_collection c" + where)
                .params(params).query((rs,n) -> {
                    String content = rs.getString("script_content");
                    var created = rs.getTimestamp("created_at");
                    return new Evidence(new Metadata(rs.getString("task_id"), rs.getString("namespace"), rs.getString("project_key"),
                            rs.getString("external_request_id"), rs.getString("activity_type"), created == null ? null : created.toInstant()),
                            new Input(rs.getString("script_source"), rs.getString("script_key"), rs.getString("script_version"),
                                    rs.getString("script_policy"), rs.getString("parser_type"), rs.getString("script_sha256"), content == null ? "UNAVAILABLE" : "AVAILABLE", content),
                            submission(rs.getString("submission_snapshot_json")), null);
                }).optional().map(e -> new Evidence(e.metadata(), e.input(), e.submission(), facts(e.metadata().collectionId())));
    }
    private ExecutionFacts facts(String id) {
        var targets = jdbc.sql("SELECT id,device_key,protocol,host,port,username,host_key_fingerprint,status FROM device_ops_collection_target WHERE task_id=:id ORDER BY id")
                .param("id", id).query((rs,n) -> new Target(rs.getLong("id"), rs.getString("device_key"), rs.getString("protocol"), rs.getString("host"),
                        rs.getInt("port"), rs.getString("username"), rs.getString("host_key_fingerprint"), rs.getString("status"))).list();
        var parsing = jdbc.sql("SELECT log_type,release_id,input_format,result_consumer_id FROM device_ops_collection_parser_request WHERE task_id=:id")
                .param("id", id).query((rs,n) -> new SemanticParsing(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4))).optional().orElse(null);
        return new ExecutionFacts(targets, parsing);
    }
    private Submission submission(String json) {
        if (json == null) return new Submission("RECONSTRUCTED_FACTS", null,
                List.of("submission.request", "body.connection.savedConnectionId", "body.commandTimeoutSeconds", "body.parseTimeoutSeconds", "body.leaseGraceSeconds", "body.semanticParsing", "body.extensions", "body.script.parserConfig", "body.callbackUrl"));
        try {
            Map<String,Object> envelope = JSON.readValue(json, new TypeReference<>() {});
            Object omitted = envelope.remove("omittedFields");
            List<String> paths = omitted instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
            return new Submission("CAPTURED_SUBMISSION", Collections.unmodifiableMap(envelope), paths);
        } catch (Exception failure) { throw new IllegalStateException("Invalid persisted submission evidence", failure); }
    }
    private String grants(String column, String prefix, List<String> grants, Map<String,Object> params) {
        var terms = new ArrayList<String>();
        for (int i=0; i<grants.size(); i++) { String key=prefix+i; params.put(key, grants.get(i)); terms.add(exact(column, ":"+key)); }
        return terms.isEmpty() ? "1=0" : "(" + String.join(" OR ", terms) + ")";
    }
    private String exact(String column, String value) {
        return "("+column+"="+value+" AND CAST("+column+" AS "+binaryType+")=CAST("+value+" AS "+binaryType+"))";
    }
}
