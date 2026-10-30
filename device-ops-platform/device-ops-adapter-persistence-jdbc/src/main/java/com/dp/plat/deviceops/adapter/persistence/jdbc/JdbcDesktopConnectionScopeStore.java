package com.dp.plat.deviceops.adapter.persistence.jdbc;

import org.springframework.jdbc.core.simple.JdbcClient;
import java.util.*;

/** Desktop-only business bindings. Legacy connection metadata and credentials remain unchanged. */
public final class JdbcDesktopConnectionScopeStore {
    private final JdbcClient jdbc;
    public JdbcDesktopConnectionScopeStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Set<String> matching(Scope query) {
        String selection = query.deviceId() == null ? "project_id=:project" : "device_id=:device";
        return new HashSet<>(jdbc.sql("SELECT connection_id FROM device_ops_desktop_connection_scope "
                        + "WHERE owner_id=:owner AND namespace=:namespace AND protocol=:protocol AND " + selection)
                .param("owner",query.ownerId()).param("namespace",query.namespace()).param("protocol",query.protocol())
                .param("project",query.projectId()).param("device",query.deviceId())
                .query(String.class).list());
    }

    public void bind(Binding command) {
        var scope = command.scope();
        var existing = jdbc.sql("SELECT project_id,device_id,protocol FROM device_ops_desktop_connection_scope "
                        + "WHERE connection_id=:id AND owner_id=:owner AND namespace=:namespace")
                .param("id",command.connectionId()).param("owner",scope.ownerId()).param("namespace",scope.namespace())
                .query((rs,n) -> new Scope(scope.ownerId(),scope.namespace(),rs.getString(1),rs.getString(2),rs.getString(3))).optional();
        if (existing.isPresent()) {
            if (!existing.get().equals(scope)) throw new IllegalStateException("desktop connection binding differs");
            return;
        }
        jdbc.sql("INSERT INTO device_ops_desktop_connection_scope(connection_id,owner_id,namespace,project_id,device_id,protocol) "
                        + "VALUES(:id,:owner,:namespace,:project,:device,:protocol)")
                .param("id",command.connectionId()).param("owner",scope.ownerId()).param("namespace",scope.namespace())
                .param("project",scope.projectId()).param("device",scope.deviceId()).param("protocol",scope.protocol()).update();
    }

    public record Scope(String ownerId, String namespace, String projectId, String deviceId, String protocol) {
        public Scope {
            if (ownerId==null || ownerId.isBlank() || namespace==null || namespace.isBlank()
                    || projectId==null || projectId.isBlank() || !Set.of("SSH2","TELNET").contains(Objects.toString(protocol,""))
                    || (deviceId!=null && deviceId.isBlank())) throw new IllegalArgumentException("desktop connection scope required");
        }
    }
    public record Binding(Scope scope, String connectionId) { }
}
