package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CollectionLogFilePolicy implements FileBusinessObjectPolicyProvider {
    private final CollectionTaskMapper tasks;
    private final ProjectScopeApi projects;
    private final PermissionApi permissions;
    public String ownerContext() { return "PLT"; }
    public String objectType() { return "CollectionTask"; }

    public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        return policy(query.tenantId(), query.actorUserId(), query.objectId(), query.purposeCode(),
                query.referenceKey(), query.requiredAction());
    }
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        tasks.selectByTenantAndPlatformTaskIdForUpdate(query.tenantId(), query.objectId());
        return policy(query.tenantId(), query.actorUserId(), query.objectId(), query.purposeCode(),
                query.referenceKey(), query.requiredAction());
    }
    private FileBusinessObjectPolicyFact policy(Long tenant, Long actor, String id, String purpose,
                                                String referenceKey, String action) {
        var task = tasks.selectByTenantAndPlatformTaskId(tenant, id);
        boolean allowed = task != null && "COLLECTION_LOG".equals(purpose)
                && Set.of("READ", "DOWNLOAD", "PREVIEW").contains(action)
                && task.getResultVersion() != null && ("result-" + task.getResultVersion()).equals(referenceKey);
        if (allowed) {
            String permission;
            if ("IMP".equals(task.getSourceContext()) && "JointTest".equals(task.getSourceObjectType())) {
                permission = "pms:imp-joint-test:query";
            } else if ("IMP".equals(task.getSourceContext()) && "Configuration".equals(task.getSourceObjectType())) {
                permission = "pms:imp-configuration:query";
            } else if ("PLT".equals(task.getSourceContext()) && "CollectionCenter".equals(task.getSourceObjectType())) {
                permission = "pms:device-collection:query";
            } else {
                permission = "pms:collection:query";
            }
            allowed = permissions.hasAnyPermissions(actor, permission);
        }
        if (allowed) {
            Long project = Long.valueOf(task.getProjectId());
            var scope = projects.resolveCurrent(new ProjectCurrentScopeQuery(tenant, actor, project, ProjectScopeApi.ACTION_VIEW));
            allowed = scope != null && scope.fullProjectIds().contains(project);
        }
        return new FileBusinessObjectPolicyFact(allowed, 0L, "IMMUTABLE", "SINGLE",
                Set.of("COLLECTION_LOG"), Set.of("text/plain"), 20L * 1024 * 1024, "INTERNAL");
    }
}
