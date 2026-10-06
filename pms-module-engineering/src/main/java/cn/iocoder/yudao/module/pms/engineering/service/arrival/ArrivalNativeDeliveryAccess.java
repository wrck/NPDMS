package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.ArrivalDeliveryOwnerQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialUploadPolicyValidator;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;

/** Reuse public delivery actions with actual arrival Owner authority; file uploads keep their native stable key. */
@Component @RequiredArgsConstructor
public class ArrivalNativeDeliveryAccess implements DeliveryMaterialUploadPolicyValidator {
    private final ArrivalMapper arrivals;
    private final PermissionApi permissions;
    private final ProjectScopeApi scopes;
    private final ProjectAcceptanceContextApi projects;
    @Override public String ownerModule() { return "IMP"; }
    @Override public boolean supportsEntityType(String type) { return "arrival".equals(type); }
    @Override public Long requireDeliveryAccess(Long tenant, Long actor, String type, String objectId,
            String purpose, boolean write, boolean lock, Long expectedScope) {
        var principal = SecurityFrameworkUtils.getLoginUser();
        if (!Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()) || actor == null || actor <= 0
                || principal == null || !Objects.equals(actor, principal.getId()) || !Objects.equals(tenant, principal.getTenantId())
                || !supportsEntityType(type)) throw denied();
        Long id; try { id = Long.valueOf(objectId); } catch (RuntimeException invalid) { throw denied(); }
        if (id <= 0) throw denied();
        var row = lock ? arrivals.selectDeliveryOwnerForUpdate(new ArrivalDeliveryOwnerQuery(tenant, id)) : arrivals.selectById(id);
        if (row == null || !Objects.equals(tenant, row.getTenantId()) || row.getProjectId() == null
                || !permissions.hasAnyPermissions(actor, "pms:imp-arrival:query")) throw denied();
        if (write && (!ArrivalDocumentSources.SOURCE_CODE.equals(purpose)
                || !permissions.hasAnyPermissions(actor, "pms:imp-arrival:update")
                || !(Integer.valueOf(0).equals(row.getStatus()) || Integer.valueOf(2).equals(row.getStatus())))) throw denied();
        String action = write ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_VIEW;
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant, actor, row.getProjectId(), action));
        if (!visible(scope, row.getProjectId()) || expectedScope != null && !Objects.equals(expectedScope, scope.treeVersion())) throw denied();
        if (lock) {
            var checked = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant, actor, row.getProjectId(), action, scope.treeVersion()));
            if (!visible(checked, row.getProjectId()) || !Objects.equals(scope.treeVersion(), checked.treeVersion())) throw denied();
        }
        if (write) {
            var query = new ProjectAcceptanceContextApi.Query(tenant, row.getProjectId(), actor);
            var observed = projects.inspect(query); if (observed == null) throw denied();
            var checked = lock ? projects.lock(query, observed.projectVersion(), scope.treeVersion()) : observed;
            if (checked == null || !Objects.equals(row.getProjectId(), checked.projectId())
                    || !Objects.equals(scope.treeVersion(), checked.treeVersion()) || !"ACTIVE".equals(checked.lifecycleStatus())) throw denied();
        }
        return scope.treeVersion();
    }
    @Override public FileBusinessObjectPolicyFact validateUpload(Long tenant, Long actor, String type, String id,
            String purpose, String action, boolean lock, Long expectedScope) {
        // Existing IMP/ARRIVAL policy owns upload/replace/detach. Do not introduce a second anonymous slot.
        throw new BusinessContractException("ARRIVAL_NATIVE_FILE_COMMAND_REQUIRED", "Use the native arrival file stable key");
    }
    private static boolean visible(ProjectScopeResult scope, Long id) {
        return scope != null && scope.treeVersion() != null && scope.treeVersion() >= 0 && scope.fullProjectIds() != null
                && scope.fullProjectIds().contains(id) && (scope.placeholderProjectIds() == null || !scope.placeholderProjectIds().contains(id));
    }
    private static BusinessContractException denied() {
        return new BusinessContractException("ARRIVAL_DELIVERY_ACCESS_DENIED", "Native arrival permission, state, tenant, scope or project lifecycle denied delivery action");
    }
}
