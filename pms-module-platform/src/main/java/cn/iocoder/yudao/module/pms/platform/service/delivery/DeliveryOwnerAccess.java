package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialUploadPolicyValidator;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import org.springframework.beans.factory.ObjectProvider;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Objects;

/** Authenticated public delivery access; internal projection APIs retain their Owner contracts. */
@Component
public class DeliveryOwnerAccess {
    private final List<DeliveryMaterialUploadPolicyValidator> owners;
    private final BusinessEntityPersistenceRegistry persistence;
    private final BusinessAccessGuard permissions;
    private final ObjectProvider<EntityFieldProvider> entityOwners;
    private final ProjectScopeApi scopes;
    private final cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi projectContexts;

    private final DeclaredBusinessDeliveryBridge declared;
    @org.springframework.beans.factory.annotation.Autowired
    public DeliveryOwnerAccess(List<DeliveryMaterialUploadPolicyValidator> owners,BusinessEntityPersistenceRegistry persistence,BusinessAccessGuard permissions,
            ObjectProvider<EntityFieldProvider> entityOwners,ProjectScopeApi scopes,cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi projectContexts,
            DeclaredBusinessDeliveryBridge declared) {
        this.owners=owners;this.persistence=persistence;this.permissions=permissions;this.entityOwners=entityOwners;this.scopes=scopes;this.projectContexts=projectContexts;this.declared=declared;
    }
    public DeliveryOwnerAccess(List<DeliveryMaterialUploadPolicyValidator> owners,BusinessEntityPersistenceRegistry persistence,BusinessAccessGuard permissions,
            ObjectProvider<EntityFieldProvider> entityOwners,ProjectScopeApi scopes,cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi projectContexts) {
        this(owners,persistence,permissions,entityOwners,scopes,projectContexts,null);
    }
    public boolean allowsGenericDeliveryActions(String module,String type) {
        var custom=owners.stream().filter(p->module.equals(p.ownerModule())&&p.supportsEntityType(type)).toList();
        if(custom.size()>1)throw denied();
        return custom.isEmpty()||custom.getFirst().allowsGenericDeliveryActions(type);
    }

    public Long require(String module, String type, Long id, String purpose, boolean write, boolean lock) {
        return require(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                module, type, id, purpose, write, lock, null);
    }

    public Long projectId(String module, String type, Long id) {
        if(declared!=null && declared.supports(module,type)) return declared.projectId(TenantContextHolder.getRequiredTenantId(),module,type,id);
        if (("PRJ".equals(module) && "project".equals(type)) || ("ACC".equals(module) && "project_deliverable".equals(type))) return id;
        var declaration = persistence.require(module,type);
        var row = persistence.<BaseBusinessEntity>mapperOf(declaration).selectById(id);
        if (row == null || !TenantContextHolder.getRequiredTenantId().equals(row.getTenantId())) throw denied();
        var field = BusinessModelIntrospector.businessFields(declaration.entityClass()).stream()
                .filter(f -> "projectId".equals(f.code())).findFirst();
        return field.isEmpty() ? null : (Long) BusinessModelIntrospector.readValues(row,List.of(field.get())).get("projectId");
    }

    public Long require(Long tenant, Long actor, String module, String type, Long id, String purpose,
                        boolean write, boolean lock, Long expectedScope) {
        if (!Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()) || actor == null || actor <= 0
                || id == null || id <= 0) throw denied();
        var custom = owners.stream().filter(p -> module.equals(p.ownerModule()) && p.supportsEntityType(type)).toList();
        if (custom.size() > 1) throw denied();
        if (custom.size() == 1) return custom.getFirst().requireDeliveryAccess(
                tenant, actor, type, id.toString(), purpose, write, lock, expectedScope);
        if(declared!=null && declared.supports(module,type)) return declared.require(tenant,actor,module,type,id,write,lock,expectedScope);
        var declaration = persistence.require(module, type);
        var user = new EntityActor(tenant, actor, "DELIVERY_ACCESS");
        permissions.requireReadable(declaration.descriptor(), user, "delivery");
        BaseBusinessEntity row = persistence.<BaseBusinessEntity>mapperOf(declaration).selectById(id);
        if (row == null || !tenant.equals(row.getTenantId())) throw denied();
        var ref = EntityDataRef.current(new EntityRef(tenant, module, type, id));
        var fields = BusinessModelIntrospector.businessFields(declaration.entityClass());
        Long project = "PRJ".equals(module) && "project".equals(type) ? id : null;
        var projectField = fields.stream().filter(f -> "projectId".equals(f.code())).findFirst();
        if (projectField.isPresent()) {
            Object value = BusinessModelIntrospector.readValues(row, List.of(projectField.get())).get("projectId");
            if (!(value instanceof Long)) throw denied();
            project = (Long) value;
        }
        if (project == null) {
            // Non-project Owners must supply actual scope/lifecycle checks, not a permission-only fallback.
            var providers = entityOwners.orderedStream().filter(p -> module.equals(p.ownerModule()) && type.equals(p.entityType())).toList();
            if (providers.size() != 1) throw denied();
            providers.getFirst().requireReadable(ref, user);
            if (write) providers.getFirst().lockForWrite(ref, user, row.getVersion());
            if (expectedScope != null && !Objects.equals(expectedScope, row.getVersion())) throw denied();
            return row.getVersion();
        }
        String action = write ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_VIEW;
        var observed = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant, actor, project, action));
        if (observed == null || observed.treeVersion() == null || observed.fullProjectIds() == null
                || !observed.fullProjectIds().contains(project)
                || expectedScope != null && !expectedScope.equals(observed.treeVersion())) throw denied();
        if (lock) {
            var current = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                    tenant, actor, project, action, observed.treeVersion()));
            if (current == null || current.fullProjectIds() == null || !current.fullProjectIds().contains(project)
                    || !Objects.equals(current.treeVersion(), observed.treeVersion())) throw denied();
        }
        if (write) {
            var query = new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Query(tenant,project,actor);
            var initial = projectContexts.inspect(query);
            if (initial == null || !Objects.equals(initial.projectId(),project)) throw denied();
            var current = lock ? projectContexts.lock(query,initial.projectVersion(),observed.treeVersion()) : initial;
            if (current == null || !Objects.equals(current.projectId(),project)
                    || !Objects.equals(current.treeVersion(),observed.treeVersion())
                    || !"ACTIVE".equals(current.lifecycleStatus())) throw denied();
        }
        return observed.treeVersion();
    }
    static BusinessContractException denied() {
        return new BusinessContractException("DELIVERY_ACCESS_DENIED", "无交付件来源对象权限或范围已变化");
    }
}
