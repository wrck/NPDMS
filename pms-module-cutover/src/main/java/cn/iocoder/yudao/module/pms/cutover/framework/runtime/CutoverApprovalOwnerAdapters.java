package cn.iocoder.yudao.module.pms.cutover.framework.runtime;

import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.system.api.permission.RoleCandidateFactApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;
import java.util.*;

@Configuration(proxyBeanMethods = false)
public class CutoverApprovalOwnerAdapters {
    @Bean
    CutoverApprovalProjectScopePort cutoverApprovalProjectScopePort(CutoverProjectScopePort scopes) {
        return new CutoverApprovalProjectScopePort() {
            public ProjectScopeFact inspect(CutoverApprovalProjectScopePort.ScopeQuery query) {
        long tenant = query.tenantId();
        long project = query.projectId();
        long actor = query.subjectUserId();
        String action = query.requiredAction();

                CutoverOwnerAdapters.requireTenant(tenant);
                var result = scopes.inspect(actor, project, action);
                return new ProjectScopeFact(tenant, project, actor, action, result.allowed(), result.projectScopeVersion());
            }
            public ProjectScopeRevalidation lockAndRevalidate(ProjectScopeFact expected) {
                CutoverOwnerAdapters.requireTenant(expected.tenantId());
                var result = scopes.lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery.builder()
                .actorId(expected.subjectUserId())
                .projectId(expected.projectId())
                .action(expected.requiredAction())
                .expectedProjectScopeVersion(expected.treeVersion())
                .build());
                var current = new ProjectScopeFact(expected.tenantId(), expected.projectId(), expected.subjectUserId(), expected.requiredAction(), result.allowed(), result.projectScopeVersion());
                return new ProjectScopeRevalidation(current.equals(expected) ? Revalidation.VALID : Revalidation.STALE, current);
            }
        };
    }
    @Bean
    ProjectCutoverRuntimeServiceManagerPort projectCutoverServiceManagerPort(ProjectBusinessContextApi projects,
            CutoverProjectScopePort scopes, AdminUserApi users) {
        return new ProjectCutoverRuntimeServiceManagerPort() {
            public ServiceManagerFact inspectCurrent(long tenant, long project, LocalDateTime checked) {
                var query = query(tenant, project);
                return fact(query, checked, projects.serviceManagers(query));
            }
            public ServiceManagerRevalidation lockAndRevalidate(ServiceManagerFact expected) {
                var query = query(expected.tenantId(), expected.projectId());
                var inspected = projects.serviceManagers(query);
                var locked = projects.lockServiceManagers(ProjectBusinessContextApi.Revalidation.builder()
                .query(query)
                .expectedProjectVersion(inspected.projectVersion()).build());
                var current = fact(query, expected.checkedAt(), locked);
                return new ServiceManagerRevalidation(current.outcome() == Outcome.FOUND && current.equals(expected) ? Revalidation.VALID : Revalidation.STALE, current);
            }
            private ProjectBusinessContextApi.Query query(long tenant, long project) {
                CutoverOwnerAdapters.requireTenant(tenant);
                var scope = scopes.inspect(CutoverOwnerAdapters.actor(), project, "ACTION_VIEW");
                if (!scope.allowed()) throw new SecurityException("无项目范围");
                return ProjectBusinessContextApi.Query.builder()
                .tenantId(tenant)
                .actorId(CutoverOwnerAdapters.actor())
                .projectId(project)
                .expectedScopeVersion(scope.projectScopeVersion()).build();
            }
            private ServiceManagerFact fact(ProjectBusinessContextApi.Query query, LocalDateTime checked,
                                            ProjectBusinessContextApi.ServiceManagers candidates) {
                long tenant = query.tenantId();
                long project = query.projectId();
                if (candidates.userIds().size() != 1) return new ServiceManagerFact(Outcome.NOT_UNIQUE, tenant, project, null, null, null, null, checked);
                Long user = candidates.userIds().getFirst();
                users.validateUser(user);
                return new ServiceManagerFact(Outcome.FOUND, tenant, project, user, "SERVICE_MANAGER",
                        Math.toIntExact(candidates.projectVersion()), candidates.projectVersion(), checked);
            }
        };
    }
    @Bean
    CutoverApprovalRoleCandidatePort cutoverApprovalRoleCandidatePort(RoleCandidateFactApi api) {
        return new CutoverApprovalRoleCandidatePort() {
            public CandidateSet inspectCandidates(RoleQuery query) { return candidates(query, false); }
            public CandidateRevalidation lockAndRevalidate(CandidateSet expected) {
                var current = candidates(new RoleQuery(expected.tenantId(), expected.roleGroupCode()), true);
                return new CandidateRevalidation(current.equals(expected) ? Revalidation.VALID : Revalidation.STALE, current);
            }
            public RoleReference inspectRole(RoleQuery query) {
                CutoverOwnerAdapters.requireTenant(query.tenantId());
                var role = api.inspectRole(new RoleCandidateFactApi.Query(query.tenantId(), query.roleGroupCode()));
                return role == null ? null : new RoleReference(query.tenantId(), role.roleId(), role.roleCode());
            }
            public List<ActorMembership> inspectActor(ActorQuery query) { return actor(query, false); }
            public List<ActorMembership> lockAndInspectActor(ActorQuery query) { return actor(query, true); }
            private List<ActorMembership> actor(ActorQuery query, boolean locking) {
                CutoverOwnerAdapters.requireTenant(query.tenantId());
                var request = new RoleCandidateFactApi.ActorQuery(query.tenantId(), query.actorId());
                var rows = locking ? api.lockAndInspectActor(request) : api.inspectActor(request);
                return rows.stream().filter(row -> Set.of("probSupporter", "probRD", "CUT_SECOND_LINE_APPROVER", "CUT_RND_APPROVER")
                                .contains(row.roleCode()))
                        .map(row -> new ActorMembership(new RoleReference(query.tenantId(), row.roleId(), row.roleCode()),
                                new Candidate(row.userId(), row.roleId(), row.membershipWatermark(), row.userWatermark())))
                        .toList();
            }
            private CandidateSet candidates(RoleQuery request, boolean locking) {
                CutoverOwnerAdapters.requireTenant(request.tenantId());
                var query = new RoleCandidateFactApi.Query(request.tenantId(), request.roleGroupCode());
                var result = locking ? api.lockAndInspect(query) : api.inspect(query);
                return new CandidateSet(request.tenantId(), request.roleGroupCode(), result.stream().map(value -> new Candidate(value.userId(), value.roleId(),
                        value.membershipWatermark(), value.userWatermark())).toList());
            }
        };
    }
}
