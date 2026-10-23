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
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper parties;

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
        var current = inspectProject(query);
        return ordinaryContext(query, current.project(), current.treeVersion());
    }

    private record ContactProject(ProjectMasterDO project, Long treeVersion) {}

    private ContactProject inspectProject(Query query) {
        validate(query);
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(query.tenantId())
                .subjectUserId(query.actorUserId())
                .anchorProjectId(query.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (scope == null || !scope.fullProjectIds().contains(query.projectId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var project = requireProject(query, projects.selectById(query.projectId()));
        return new ContactProject(project, scope.treeVersion());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Context lockForWrite(WriteQuery write) {
        return lockContext(write, false);
    }

    @Override
    public FinalCustomer inspectFinalCustomer(Query query) {
        inspectProject(query);
        return finalCustomer(query, false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public FinalCustomer lockHistoricalFinalCustomer(WriteQuery write) {
        lockContext(write, true);
        return finalCustomer(new Query(write.tenantId(), write.actorUserId(), write.projectId()), true);
    }

    private FinalCustomer finalCustomer(Query query, boolean lock) {
        var rows=parties.selectHeaderFinalCustomers(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper.HeaderFinalCustomerQuery(query.tenantId(),query.projectId()));
        if(rows.isEmpty()) return null;
        if(rows.size()!=1) throw exception(PROJECT_VERSION_CONFLICT);
        var party=rows.getFirst();
        if(lock) party=parties.selectSourceForUpdate(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper.PartySourceQuery(
                query.tenantId(),"DPPMS","pm_project_header",party.getSourceRecordKey(),"FINAL_CUSTOMER"));
        if(party==null || !Objects.equals(party.getTenantId(),query.tenantId()) || !Objects.equals(party.getProjectId(),query.projectId())
                || Boolean.TRUE.equals(party.getDeleted()) || !"ACTIVE".equals(party.getStatus())
                || party.getPartyName()==null || party.getPartyName().isBlank()) throw exception(PROJECT_VERSION_CONFLICT);
        return new FinalCustomer(party.getId(),party.getProjectId(),party.getPartyName(),party.getSourceRecordKey());
    }

    private Context lockContext(WriteQuery write, boolean historical) {
        var query = new Query(write.tenantId(), write.actorUserId(), write.projectId());
        validate(query);
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(query.tenantId())
                .subjectUserId(query.actorUserId())
                .anchorProjectId(query.projectId())
                .actionCode(ProjectScopeApi.ACTION_MANAGE).build());
        if (scope == null || !scope.fullProjectIds().contains(query.projectId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        scope = scopes.lockAndRevalidate(ProjectScopeRevalidationQuery.builder()
                .tenantId(query.tenantId())
                .subjectUserId(query.actorUserId())
                .anchorProjectId(query.projectId())
                .actionCode(ProjectScopeApi.ACTION_MANAGE)
                .expectedScopeVersion(scope.treeVersion()).build());
        if (scope == null || !scope.fullProjectIds().contains(query.projectId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var project = requireProject(query, projects.selectByIdForUpdate(query.projectId()));
        if (write.expectedProjectVersion() == null || !Objects.equals(project.getVersion(), write.expectedProjectVersion())) throw exception(PROJECT_VERSION_CONFLICT);
        var result = historical ? context(query, project, scope.treeVersion())
                : ordinaryContext(query, project, scope.treeVersion());
        if (historical ? !result.canViewHistory() || !Set.of("ACTIVE","NORMAL_CLOSED","EXCEPTION_CLOSED").contains(result.lifecycleStatus()) : !result.canManage())
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        return result;
    }

    private Context context(Query query, ProjectMasterDO project, Long treeVersion) {
        boolean manager = projectRoles.isTenantSuperAdmin(query.tenantId(), query.actorUserId())
                || !members.selectParticipantFacts(ProjectParticipantFactLookupQuery.builder()
                .tenantId(query.tenantId())
                .projectId(query.projectId())
                .subjectUserId(query.actorUserId())
                .requiredRoleCodes(Set.of("PROJECT_MANAGER"))
                .effectiveAt(LocalDateTime.now()).build()).isEmpty();
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

    private Context ordinaryContext(Query query, ProjectMasterDO project, Long treeVersion) {
        var current = context(query, project, treeVersion);
        Long customerId = project.getCustomerId();
        if (customerId == null && project.getCustomerCode() != null && !project.getCustomerCode().isBlank()) {
            var customer = customers.getCustomerByCode(new cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerCodeQuery(
                    project.getCustomerCode(), query.actorUserId()));
            if (customer == null || !Objects.equals(customer.tenantId(), query.tenantId())) {
                throw exception(cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CUSTOMER_UNAVAILABLE);
            }
            customerId = customer.id();
        }
        return new Context(current.projectId(), customerId, current.projectVersion(), current.lifecycleStatus(),
                current.canManage(), current.canViewHistory());
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
