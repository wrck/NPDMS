package com.dp.plat.deviceops.adapter.persistence.jdbc;

import com.dp.plat.deviceops.core.port.CallbackOutboxPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

/** Local capabilities are encrypted separately from immutable collection evidence. */
public final class JdbcDesktopTaskStore {
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final AesGcmCredentialCipher cipher;
    public JdbcDesktopTaskStore(JdbcClient jdbc, TransactionTemplate transaction, AesGcmCredentialCipher cipher) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.cipher = cipher;
    }

    public Task register(Registration command) {
        var key = new TaskKey(command.namespace(), command.platformTaskId());
        var existing = find(key);
        if (existing.isEmpty()) {
            char[] token = command.token().toCharArray();
            AesGcmCredentialCipher.EncryptedValue encrypted;
            try { encrypted = cipher.encrypt(token); }
            finally { Arrays.fill(token, '\0'); }
            try {
                jdbc.sql("""
                        INSERT INTO device_ops_desktop_task(namespace,platform_task_id,tenant_id,owner_id,project_id,
                          execution_id,token_ciphertext,token_nonce,token_key_version)
                        VALUES(:namespace,:task,:tenant,:owner,:project,:execution,:token,:nonce,:version)
                        """).param("namespace", command.namespace()).param("task", command.platformTaskId())
                        .param("tenant", command.tenantId()).param("owner", command.ownerId()).param("project", command.projectId())
                        .param("execution", UUID.randomUUID().toString()).param("token", encrypted.ciphertext())
                        .param("nonce", encrypted.nonce()).param("version", AesGcmCredentialCipher.KEY_VERSION).update();
            } catch (org.springframework.dao.DuplicateKeyException concurrent) {
                if (find(key).isEmpty()) throw concurrent;
            }
        }
        var task = find(key).orElseThrow();
        if (task.tenantId() != command.tenantId() || !task.ownerId().equals(command.ownerId())
                || !task.projectId().equals(command.projectId())) throw new IllegalStateException("desktop task binding differs");
        return task;
    }

    public Optional<Task> find(TaskKey key) {
        return jdbc.sql("SELECT * FROM device_ops_desktop_task WHERE namespace=:namespace AND platform_task_id=:task")
                .param("namespace", key.namespace()).param("task", key.platformTaskId()).query(this::map).optional();
    }

    public List<Task> pending() {
        // Acknowledged results still accept explicit redelivery requests, at a lower polling frequency.
        return jdbc.sql("""
                SELECT * FROM device_ops_desktop_task
                WHERE result_acknowledged=FALSE OR last_polled_at<:completedBefore
                ORDER BY last_polled_at,platform_task_id LIMIT 20
                """).param("completedBefore", Instant.now().minusSeconds(60)).query(this::map).list();
    }

    public void polled(TaskKey key) {
        jdbc.sql("UPDATE device_ops_desktop_task SET last_polled_at=CURRENT_TIMESTAMP WHERE namespace=:namespace AND platform_task_id=:task")
                .param("namespace", key.namespace()).param("task", key.platformTaskId()).update();
    }

    public void attach(Attachment command) {
        if (jdbc.sql("""
                UPDATE device_ops_desktop_task SET collection_id=:collection WHERE namespace=:namespace AND platform_task_id=:task
                  AND (collection_id IS NULL OR collection_id=:collection)
                """).param("namespace", command.task().namespace()).param("task", command.task().platformTaskId())
                .param("collection", command.collectionId()).update() != 1) throw new IllegalStateException("desktop collection binding differs");
    }

    public void acknowledge(TaskKey key) {
        jdbc.sql("UPDATE device_ops_desktop_task SET result_acknowledged=TRUE WHERE namespace=:namespace AND platform_task_id=:task")
                .param("namespace", key.namespace()).param("task", key.platformTaskId()).update();
    }

    public char[] token(Task task) {
        return cipher.decrypt(task.tokenCiphertext(), task.tokenNonce(), task.tokenKeyVersion());
    }

    /** Claim only desktop-owned events so standalone OIDC callbacks retain their existing dispatcher. */
    public List<CallbackOutboxPort.Event> claimDue(Due query) {
        return transaction.execute(status -> {
            var ids = jdbc.sql("""
                    SELECT o.event_id FROM device_ops_outbox o
                    JOIN device_ops_desktop_task d ON d.collection_id=o.aggregate_id
                    WHERE o.delivered_at IS NULL AND o.dead_lettered_at IS NULL AND o.next_attempt_at<=:now
                      AND (o.lease_until IS NULL OR o.lease_until<:now)
                    ORDER BY o.next_attempt_at,o.event_id LIMIT :limit
                    """).param("now", query.now()).param("limit", query.limit()).query(String.class).list();
            List<CallbackOutboxPort.Event> events = new ArrayList<>();
            for (String id : ids) {
                int updated = jdbc.sql("""
                        UPDATE device_ops_outbox SET lease_owner=:owner,lease_until=:until WHERE event_id=:id
                          AND delivered_at IS NULL AND dead_lettered_at IS NULL AND (lease_until IS NULL OR lease_until<:now)
                        """).param("owner", query.worker()).param("until", query.until()).param("id", id).param("now", query.now()).update();
                if (updated == 1) events.add(jdbc.sql("SELECT event_id,destination,payload,attempt_count FROM device_ops_outbox WHERE event_id=:id")
                        .param("id", id).query((rs, n) -> new CallbackOutboxPort.Event(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4))).single());
            }
            return events;
        });
    }

    private Task map(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        return new Task(rs.getString("namespace"), rs.getString("platform_task_id"), rs.getLong("tenant_id"), rs.getString("owner_id"),
                rs.getString("project_id"), rs.getString("execution_id"), rs.getBytes("token_ciphertext"), rs.getBytes("token_nonce"),
                rs.getString("token_key_version"), rs.getString("collection_id"), rs.getBoolean("result_acknowledged"));
    }
    public record TaskKey(String namespace, String platformTaskId) { }
    public record Registration(String namespace, String platformTaskId, long tenantId, String ownerId, String projectId, String token) {
        @Override public String toString() { return "DesktopRegistration[redacted]"; }
    }
    public record Attachment(TaskKey task, String collectionId) { }
    public record Due(String worker, Instant now, Instant until, int limit) { }
    public record Task(String namespace, String platformTaskId, long tenantId, String ownerId, String projectId, String executionId,
                       byte[] tokenCiphertext, byte[] tokenNonce, String tokenKeyVersion, String collectionId, boolean acknowledged) {
        public TaskKey key() { return new TaskKey(namespace, platformTaskId); }
        @Override public String toString() { return "DesktopTask[" + platformTaskId + "]"; }
    }
}
