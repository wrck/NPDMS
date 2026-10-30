package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.WriteRequest;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationControlScope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact deployed command identity. Undeclared versions never inherit the latest entry policy. */
@Component
public final class ProjectOperationEntryPolicy {
    private record Key(String code, int version) { }
    private record Entry(String owner, String type, ProjectOperationControlScope scope) { }
    private final Map<Key, Entry> entries;

    public ProjectOperationEntryPolicy(List<ProjectBusinessOperationProvider> providers) {
        Map<Key, Entry> registered = new HashMap<>();
        for (var provider : providers) {
            var scopes = Objects.requireNonNull(provider.controlScopes(), "operation control scopes");
            if (scopes.isEmpty()) continue;
            var found = new HashSet<String>();
            for (var operation : provider.operations()) {
                if (!scopes.containsKey(operation.operationCode())) continue;
                var scope = Objects.requireNonNull(scopes.get(operation.operationCode()), "operation control scope");
                var key = new Key(operation.operationCode(), operation.operationVersion());
                if (registered.putIfAbsent(key, new Entry(operation.ownerContext(), operation.objectType(), scope)) != null)
                    throw new IllegalArgumentException("OPERATION_ENTRY_POLICY_DUPLICATE");
                found.add(operation.operationCode());
            }
            if (!found.equals(scopes.keySet())) throw new IllegalArgumentException("OPERATION_ENTRY_POLICY_UNKNOWN_COMMAND");
        }
        entries = Map.copyOf(registered);
    }

    public ProjectOperationControlScope scope(WriteRequest request) {
        if (request == null || request.operationCode() == null || request.operationVersion() == null) return null;
        var entry = entries.get(new Key(request.operationCode(), request.operationVersion()));
        return entry != null && Objects.equals(entry.owner(), request.ownerContext())
                && Objects.equals(entry.type(), request.objectType()) ? entry.scope() : null;
    }
}
