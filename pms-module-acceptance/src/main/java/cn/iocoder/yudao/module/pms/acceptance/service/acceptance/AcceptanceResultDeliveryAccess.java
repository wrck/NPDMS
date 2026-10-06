package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialUploadPolicyValidator;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;

/** Native result panels consume historical materials; native commands alone produce these results. */
@Component
@RequiredArgsConstructor
public class AcceptanceResultDeliveryAccess implements DeliveryMaterialUploadPolicyValidator {
 @Override public boolean allowsGenericDeliveryActions(String entityType){return false;}

    private final DeliverableChecklistMapper checklists;
    private final AcceptanceActivityMapper reports;
    private final SatisfactionCollectionTaskMapper satisfaction;
    private final PermissionApi permissions;
    private final ProjectScopeApi scopes;
    @Override public String ownerModule() { return "ACC"; }
    @Override public boolean supportsEntityType(String type) {
        return Set.of("deliverableChecklist", "acceptanceActivity", "satisfactionCollectionTask").contains(type);
    }
    @Override public Long requireDeliveryAccess(Long tenant, Long actor, String type, String objectId,
            String purpose, boolean write, boolean lock, Long expectedScope) {
        if (write || !Objects.equals(tenant, TenantContextHolder.getRequiredTenantId())
                || actor == null || actor <= 0 || !supportsEntityType(type)) throw denied();
        Long id;
        try { id = Long.valueOf(objectId); } catch (RuntimeException invalid) { throw denied(); }
        if (id <= 0) throw denied();
        Long project, version; String permission;
        switch (type) {
            case "deliverableChecklist" -> {
                var row = checklists.selectById(id);
                if (row == null || !Objects.equals(tenant, row.getTenantId())) throw denied();
                project = row.getProjectId(); version = row.getVersion();
                permission = "pms:acc-deliverable-checklist:query";
            }
            case "acceptanceActivity" -> {
                var row = reports.selectById(id);
                if (row == null || !Objects.equals(tenant, row.getTenantId())) throw denied();
                project = row.getProjectId(); version = row.getVersion();
                permission = "pms:acceptance:report:query";
            }
            case "satisfactionCollectionTask" -> {
                var row = satisfaction.selectById(id);
                // Matches SatisfactionTaskManagementService.get: only the current assignee can read this task.
                if (row == null || !Objects.equals(tenant, row.getTenantId())
                        || !Objects.equals(actor, row.getAssignedToUserId())) throw denied();
                project = row.getProjectId(); version = row.getVersion();
                permission = "pms:acceptance:satisfaction:query";
            }
            default -> throw denied();
        }
        if (project == null || version == null || version < 0
                || !permissions.hasAnyPermissions(actor, permission)) throw denied();
        var current = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant, actor, project, ProjectScopeApi.ACTION_VIEW));
        if (!visible(current, project) || expectedScope != null && !Objects.equals(expectedScope, current.treeVersion())) throw denied();
        if (lock) {
            var checked = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant, actor, project,
                    ProjectScopeApi.ACTION_VIEW, current.treeVersion()));
            if (!visible(checked, project) || !Objects.equals(current.treeVersion(), checked.treeVersion())) throw denied();
        }
        return current.treeVersion();
    }
    @Override public FileBusinessObjectPolicyFact validateUpload(Long tenant, Long actor, String type, String id,
            String purpose, String action, boolean lock, Long expectedScope) {
        // These native materials retain their original file Owner; the generic panel cannot attach files.
        throw denied();
    }
    private static boolean visible(ProjectScopeResult scope, Long project) {
        return scope != null && scope.treeVersion() != null && scope.fullProjectIds() != null
                && scope.fullProjectIds().contains(project);
    }
    private static BusinessContractException denied() {
        return new BusinessContractException("DELIVERY_ACCESS_DENIED", "Native result read permission, assignee or project scope required");
    }
}
