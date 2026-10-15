package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter;
import org.springframework.beans.factory.ObjectProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CollectionLogFilePolicy implements FileBusinessObjectPolicyProvider {
    private final CollectionTaskMapper tasks;
    private final ProjectScopeApi projects;
    private final PermissionApi permissions;
    private final ObjectProvider<CollectionSourceAdapter> sources;
    public String ownerContext() { return "PLT"; }
    public String objectType() { return "CollectionTask"; }

    public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        return policy(query);
    }
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        tasks.selectByTenantAndPlatformTaskIdForUpdate(query.tenantId(), query.objectId());
        return policy(query.toInspectionQuery());
    }
    private FileBusinessObjectPolicyFact policy(FileBusinessObjectPolicyQuery query) {
        Long tenant = query.tenantId(), actor = query.actorUserId();
        var task = tasks.selectByTenantAndPlatformTaskId(tenant, query.objectId());
        boolean allowed = task != null && "COLLECTION_LOG".equals(query.purposeCode())
                && Set.of("READ", "DOWNLOAD", "PREVIEW").contains(query.requiredAction())
                && task.getResultVersion() != null && ("result-" + task.getResultVersion()).equals(query.referenceKey());
        if (allowed) {
            String permission;
            if ("IMP".equals(task.getSourceContext()) && "JointTest".equals(task.getSourceObjectType())) {
                permission = "pms:imp-joint-test:query";
            } else if ("IMP".equals(task.getSourceContext()) && "Configuration".equals(task.getSourceObjectType())) {
                permission = "pms:imp-configuration:query";
            } else if ("PLT".equals(task.getSourceContext()) && "CollectionCenter".equals(task.getSourceObjectType())) {
                permission = "pms:device-collection:query";
            } else if ("SRV".equals(task.getSourceContext()) && "INSPECTION_EXECUTION".equals(task.getSourceObjectType())) {
                permission = "pms:inspection-task:query";
                var adapters = sources.orderedStream().filter(s -> "inspection".equals(s.entry())).toList();
                if (adapters.size() != 1) allowed = false;
                else {
                    var source = adapters.getFirst().authorize(CollectionSourceAdapter.AuthorizationQuery.builder()
                            .tenantId(tenant).actorId(actor).objectId(Long.valueOf(task.getSourceObjectId()))
                            .deviceId(Long.valueOf(task.getDeviceId())).access(CollectionSourceAdapter.Access.READ).build());
                    allowed = source != null && "SRV".equals(source.sourceContext())
                            && "INSPECTION_EXECUTION".equals(source.sourceObjectType())
                            && task.getProjectId().equals(String.valueOf(source.projectId()))
                            && task.getDeviceId().equals(String.valueOf(source.deviceId()));
                }
            } else {
                permission = "pms:collection:query";
            }
            allowed = allowed && permissions.hasAnyPermissions(actor, permission);
        }
        if (allowed) {
            Long project = Long.valueOf(task.getProjectId());
            var scope = projects.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(tenant)
                .subjectUserId(actor)
                .anchorProjectId(project)
                .actionCode(ProjectScopeApi.ACTION_VIEW).build());
            allowed = scope != null && scope.fullProjectIds().contains(project);
        }
        return new FileBusinessObjectPolicyFact(allowed, 0L, "IMMUTABLE", "SINGLE",
                Set.of("COLLECTION_LOG"), Set.of("text/plain"), 20L * 1024 * 1024, "INTERNAL");
    }
}
