package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectBusinessOptionsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class ProjectBusinessContextApiImpl implements ProjectBusinessContextApi {
    private final ProjectMasterMapper projects;
    private final ProjectScopeApi scopes;
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper members;
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper plans;

    @Override
    public ImplementationRequirements implementationRequirements(Query query) {
        inspect(query);
        return implementationRequirements(projects.selectById(query.projectId()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public ImplementationRequirements lockImplementationRequirements(Revalidation query) {
        lockAndRevalidate(query);
        return implementationRequirements(projects.selectByIdForUpdate(query.query().projectId()));
    }

    private ImplementationRequirements implementationRequirements(ProjectMasterDO project) {
        if (project == null) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var all = cn.iocoder.yudao.module.pms.project.domain.template.TemplateImplementationReadiness.SOURCES;
        if (project.getActivePlanVersionId() == null) return new ImplementationRequirements(null, null, false, all);
        var plan = plans.selectEffective(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery(
                project.getTenantId(), project.getId()));
        if (plan == null || !Objects.equals(plan.getId(), project.getActivePlanVersionId())) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        var snapshot = cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot());
        var policy = snapshot.getImplementationReadiness();
        return new ImplementationRequirements(plan.getId(), plan.getVersion(), policy != null,
                policy == null ? all : policy.requiredSources(snapshot.getTasks()));
    }

    @Override
    public AccessScope currentActorScope(ActorQuery query) {
        validateActor(query);
        var visible = Set.copyOf(scopes.resolveAllCurrent(new ProjectAllScopeQuery(
                query.tenantId(), query.actorId(), ProjectScopeApi.ACTION_VIEW)));
        if (visible.isEmpty()) return new AccessScope(Set.of(), Set.of());
        var managed = members.selectActiveByUser(new ActiveProjectMemberQuery(query.tenantId(), query.actorId(), LocalDateTime.now()))
                .stream().filter(row -> visible.contains(row.getProjectId()))
                .filter(row -> ProjectMemberRoles.isServiceManager(row.getMemberRole()))
                .filter(row -> row.getAssignmentType() == null || "PRIMARY".equals(row.getAssignmentType()))
                .map(row -> row.getProjectId()).collect(Collectors.toSet());
        return new AccessScope(visible, managed);
    }

    @Override
    public List<Option> searchOptions(OptionsQuery query) {
        if (query == null) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        var actor = ActorQuery.builder().tenantId(query.tenantId()).actorId(query.actorId()).build();
        validateActor(actor);
        var visible = Set.copyOf(scopes.resolveAllCurrent(new ProjectAllScopeQuery(
                actor.tenantId(), actor.actorId(), ProjectScopeApi.ACTION_VIEW)));
        if (visible.isEmpty()) return List.of();
        return projects.selectBusinessOptions(ProjectBusinessOptionsQuery.builder().tenantId(actor.tenantId())
                .visibleProjectIds(visible).keyword(query.keyword() == null ? "" : query.keyword().trim()).build()).stream()
                .map(row -> new Option(row.getId(), row.getProjectCode(), row.getProjectName(), row.getCustomerName())).toList();
    }

    private static void validateActor(ActorQuery query) {
        if (query == null || query.tenantId() == null || query.actorId() == null || query.actorId() <= 0
                || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    @Override
    public ServiceManagers serviceManagers(Query query) {
        var context = inspect(query);
        var rows = members.selectParticipantFacts(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLookupQuery.builder()
                .tenantId(query.tenantId())
                .projectId(query.projectId())
                .subjectUserId(null)
                .requiredRoleCodes(cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.SERVICE_CODES)
                .effectiveAt(java.time.LocalDateTime.now()).build());
        return new ServiceManagers(context.projectVersion(), rows.stream().map(value -> value.getUserId()).distinct().sorted().toList());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public ServiceManagers lockServiceManagers(Revalidation expected) {
        var context = lockAndRevalidate(expected);
        var query = expected.query();
        var candidates = members.selectParticipantFacts(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLookupQuery.builder()
                .tenantId(query.tenantId())
                .projectId(query.projectId())
                .subjectUserId(null)
                .requiredRoleCodes(cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.SERVICE_CODES)
                .effectiveAt(java.time.LocalDateTime.now()).build()).stream().map(value -> value.getUserId()).distinct().sorted().toList();
        var users = new java.util.ArrayList<Long>();
        for (Long user : candidates) {
            var rows = members.selectParticipantFactsForUpdate(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLockQuery.builder()
                .tenantId(query.tenantId())
                .projectId(query.projectId())
                .userId(user)
                .requiredRoleCodes(cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.SERVICE_CODES).build());
            if (!rows.isEmpty()) users.add(user);
        }
        return new ServiceManagers(context.projectVersion(), java.util.List.copyOf(users));
    }

    @Override
    public Context inspect(Query query) {
        validate(query);
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(query.tenantId())
                .subjectUserId(query.actorId())
                .anchorProjectId(query.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW).build());
        requireScope(query, scope);
        return context(query, projects.selectById(query.projectId()));
    }

    @Override
    public Presentation presentation(Query query) {
        inspect(query);
        var project = projects.selectById(query.projectId());
        context(query, project);
        var currentMembers = members.selectActiveForAssignmentState(
                new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectAssignmentStateQuery(
                        query.projectId(), LocalDateTime.now()));
        String manager = project.getManagerName();
        if (manager == null || manager.isBlank()) manager = presentationNames(currentMembers, Set.of(ProjectMemberRoles.PROJECT_MANAGER));
        return Presentation.builder().projectId(project.getId()).projectCode(project.getProjectCode())
                .projectName(project.getProjectName()).customerName(project.getCustomerName())
                .projectManagerName(manager).serviceManagerNames(presentationNames(currentMembers, ProjectMemberRoles.SERVICE_CODES))
                .salesNames(presentationNames(currentMembers, ProjectMemberRoles.SALES_CODES))
                .departmentName(project.getDepartmentName())
                .industryName(project.getIndustryName() == null || project.getIndustryName().isBlank()
                        ? project.getIndustryCode() : project.getIndustryName())
                .majorProjectLevel(project.getMajorProjectLevel()).build();
    }

    private static String presentationNames(
            List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO> members,
            Set<String> roles) {
        return members.stream().filter(member -> roles.contains(member.getMemberRole()))
                .map(cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO::getMemberName)
                .filter(name -> name != null && !name.isBlank()).distinct().sorted().collect(Collectors.joining("、"));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Context lockAndRevalidate(Revalidation expected) {
        if (expected == null || expected.expectedProjectVersion() == null) throw exception(PROJECT_VERSION_CONFLICT);
        var query = expected.query();
        validate(query);
        var scope = scopes.lockAndRevalidate(ProjectScopeRevalidationQuery.builder()
                .tenantId(query.tenantId())
                .subjectUserId(query.actorId())
                .anchorProjectId(query.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW)
                .expectedScopeVersion(query.expectedScopeVersion()).build());
        requireScope(query, scope);
        var current = context(query, projects.selectByIdForUpdate(query.projectId()));
        if (!Objects.equals(current.projectVersion(), expected.expectedProjectVersion())) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        return current;
    }

    private static void validate(Query query) {
        if (query == null || query.tenantId() == null || query.actorId() == null || query.actorId() <= 0
                || query.projectId() == null || query.projectId() <= 0 || query.expectedScopeVersion() == null
                || query.expectedScopeVersion() < 0 || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private static void requireScope(Query query, ProjectScopeResult scope) {
        if (scope == null || !scope.fullProjectIds().contains(query.projectId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        if (!Objects.equals(scope.treeVersion(), query.expectedScopeVersion())) throw exception(PROJECT_TREE_VERSION_CONFLICT);
    }

    private static Context context(Query query, ProjectMasterDO project) {
        if (project == null || !Objects.equals(project.getTenantId(), query.tenantId())) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        return Context.builder()
                .tenantId(query.tenantId())
                .projectId(project.getId())
                .projectVersion(project.getVersion())
                .scopeVersion(query.expectedScopeVersion())
                .projectCode(project.getProjectCode())
                .projectName(project.getProjectName())
                .customerId(project.getCustomerId())
                .customerCode(project.getCustomerCode())
                .customerName(project.getCustomerName())
                .departmentId(project.getDepartmentId())
                .departmentCode(project.getDepartmentCode())
                .departmentName(project.getDepartmentName())
                .majorProjectLevel(project.getMajorProjectLevel()).build();
    }
}
