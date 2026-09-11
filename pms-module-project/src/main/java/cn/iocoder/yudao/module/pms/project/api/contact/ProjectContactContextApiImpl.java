package cn.iocoder.yudao.module.pms.project.api.contact;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectAncestorQueryApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLookupQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class ProjectContactContextApiImpl implements ProjectContactContextApi {
    private final ProjectMasterMapper projects;
    private final ProjectMemberAssignmentMapper members;
    private final ProjectScopeApi scopes;
    private final ProjectAncestorQueryApi ancestors;
    private final AuthorizationGrantApi grants;
    private final cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi customers;
    private final cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService projectRoles;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Context associateCustomer(AssociateQuery query) {
        var current = lockForWrite(new WriteQuery(query.tenantId(), query.actorUserId(), query.projectId(), query.expectedProjectVersion()));
        if (current.customerId() != null) {
            if (Objects.equals(current.customerId(), query.customerId())) return current;
            throw exception(cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CONTACT_CUSTOMER_ALREADY_BOUND);
        }
        var customer = customers.getCustomer(query.customerId());
        if (customer == null || !Objects.equals(customer.tenantId(), query.tenantId()) || !"ENABLED".equals(customer.lifecycleStatus())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        if (projects.associateCustomerIfMissing(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectContactCustomerUpdate(
                query.tenantId(), query.projectId(), query.expectedProjectVersion(), customer.id(), customer.code(), customer.name(), String.valueOf(query.actorUserId()))) != 1) throw exception(PROJECT_VERSION_CONFLICT);
        return new Context(current.projectId(), customer.id(), current.projectVersion()+1, current.lifecycleStatus(), current.canManage());
    }

    @Override
    public Context inspect(Query query) {
        validate(query);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(), query.actorUserId(), query.projectId(), ProjectScopeApi.ACTION_VIEW));
        if (scope == null || !scope.fullProjectIds().contains(query.projectId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var project = requireProject(query, projects.selectById(query.projectId()));
        return context(query, project, scope.treeVersion());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Context lockForWrite(WriteQuery write) {
        var query = new Query(write.tenantId(), write.actorUserId(), write.projectId());
        validate(query);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(), query.actorUserId(), query.projectId(), ProjectScopeApi.ACTION_MANAGE));
        if (scope == null || !scope.fullProjectIds().contains(query.projectId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        scope = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(query.tenantId(), query.actorUserId(), query.projectId(), ProjectScopeApi.ACTION_MANAGE, scope.treeVersion()));
        if (scope == null || !scope.fullProjectIds().contains(query.projectId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var project = requireProject(query, projects.selectByIdForUpdate(query.projectId()));
        if (write.expectedProjectVersion() == null || !Objects.equals(project.getVersion(), write.expectedProjectVersion())) throw exception(PROJECT_VERSION_CONFLICT);
        var result = context(query, project, scope.treeVersion());
        if (!result.canManage()) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        return result;
    }

    private Context context(Query query, ProjectMasterDO project, Long treeVersion) {
        boolean manager = projectRoles.isTenantSuperAdmin(query.tenantId(), query.actorUserId())
                || !members.selectParticipantFacts(new ProjectParticipantFactLookupQuery(
                query.tenantId(), query.projectId(), query.actorUserId(), Set.of("PROJECT_MANAGER"), LocalDateTime.now())).isEmpty();
        if (!manager) {
            var ids = new HashSet<>(ancestors.getAncestors(new ProjectAncestorQuery(query.tenantId(), query.projectId(), treeVersion)).ancestorProjectIds());
            ids.add(query.projectId());
            manager = grants.listEffective(new AuthorizationGrantQuery(query.tenantId(), "USER", query.actorUserId(),
                    "PROJ", "PROJECT", ids, ProjectScopeApi.ACTION_MANAGE, LocalDateTime.now())).stream()
                    .anyMatch(grant -> Objects.equals(grant.resourceId(), query.projectId())
                            || "PROJECT_AND_DESCENDANTS".equals(grant.scopeCode()));
        }
        return new Context(project.getId(), project.getCustomerId(), project.getVersion(), project.getLifecycleStatus(),
                manager && "ACTIVE".equals(project.getLifecycleStatus()), manager);
    }

    private void validate(Query query) {
        if (query == null || query.tenantId() == null || query.actorUserId() == null || query.projectId() == null
                || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
    }

    private ProjectMasterDO requireProject(Query query, ProjectMasterDO project) {
        if (project == null || !Objects.equals(query.tenantId(), project.getTenantId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        return project;
    }
}
