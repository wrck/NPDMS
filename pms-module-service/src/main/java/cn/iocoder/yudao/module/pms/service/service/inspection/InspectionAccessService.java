package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InspectionAccessService {
    private final ProjectScopeApi scopes;
    private final ProjectBusinessContextApi projects;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    private final InspectionTaskMapper tasks;

    public record TaskAccess(InspectionRequestContext context, Long taskId, String permission,
                             boolean write, boolean managerOnly) { }
    public record ProjectAccess(InspectionRequestContext context, Long projectId, boolean lock) { }
    public record Subject(InspectionRequestContext context, Long projectId, Long userId, String permission) { }

    public Set<Long> visibleProjects(InspectionRequestContext context) {
        return Set.copyOf(scopes.resolveAllCurrent(new ProjectAllScopeQuery(
                context.tenantId(), context.actorId(), ProjectScopeApi.ACTION_VIEW)));
    }

    public ProjectBusinessContextApi.AccessScope queryScope(InspectionRequestContext context) {
        return projects.currentActorScope(ProjectBusinessContextApi.ActorQuery.builder()
                .tenantId(context.tenantId()).actorId(context.actorId()).build());
    }

    public ProjectBusinessContextApi.Context project(ProjectAccess request) {
        var context = request.context();
        var query = projectQuery(request);
        var project = projects.inspect(query);
        if (project == null) forbidden();
        if (!request.lock()) return project;
        return projects.lockAndRevalidate(ProjectBusinessContextApi.Revalidation.builder()
                .query(ProjectBusinessContextApi.Query.builder().tenantId(context.tenantId()).actorId(context.actorId())
                        .projectId(request.projectId()).expectedScopeVersion(project.scopeVersion()).build())
                .expectedProjectVersion(project.projectVersion()).build());
    }

    public boolean manager(ProjectAccess request) {
        var context = request.context();
        var query = projectQuery(request);
        var inspected = projects.inspect(query);
        var fact = request.lock() ? projects.lockServiceManagers(ProjectBusinessContextApi.Revalidation.builder()
                .query(ProjectBusinessContextApi.Query.builder().tenantId(context.tenantId()).actorId(context.actorId())
                        .projectId(request.projectId()).expectedScopeVersion(inspected.scopeVersion()).build())
                .expectedProjectVersion(inspected.projectVersion()).build())
                : projects.serviceManagers(query);
        return fact != null && fact.userIds() != null && fact.userIds().contains(context.actorId());
    }

    private ProjectBusinessContextApi.Query projectQuery(ProjectAccess request) {
        var context = request.context();
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(context.tenantId())
                .subjectUserId(context.actorId()).anchorProjectId(request.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (scope == null || !scope.fullProjectIds().contains(request.projectId())) forbidden();
        return ProjectBusinessContextApi.Query.builder().tenantId(context.tenantId()).actorId(context.actorId())
                .projectId(request.projectId()).expectedScopeVersion(scope.treeVersion()).build();
    }

    public InspectionTaskDO task(TaskAccess request) {
        var context = request.context();
        permission(context, request.permission());
        var task = tasks.selectById(request.taskId());
        if (task == null || !Objects.equals(task.getTenantId(), context.tenantId()))
            throw new InspectionException(InspectionException.Reason.NOT_FOUND, "巡检任务不存在或不可见");
        var project = project(new ProjectAccess(context, task.getProjectId(), request.write()));
        boolean manager = manager(new ProjectAccess(context, task.getProjectId(), request.write()));
        if (request.managerOnly() ? !manager : (!Objects.equals(task.getOwnerUserId(), context.actorId()) && !manager))
            forbidden();
        if (request.write()) {
            task = tasks.selectForUpdate(new InspectionTaskRows(context.tenantId(), request.taskId()));
            if (task == null || !Objects.equals(task.getProjectId(), project.projectId()))
                throw new InspectionException(InspectionException.Reason.STATE_CONFLICT, "巡检任务归属已变化");
            if (request.managerOnly() ? !manager : (!Objects.equals(task.getOwnerUserId(), context.actorId()) && !manager))
                forbidden();
            if (!Objects.equals(task.getVersion(), context.requireVersion()))
                throw new InspectionException(InspectionException.Reason.VERSION_CONFLICT,
                        "巡检任务已由其他操作更新，请刷新后重试", task.getVersion());
            if (Set.of("ARCHIVED", "CANCELLED").contains(task.getStatusCode()))
                throw new InspectionException(InspectionException.Reason.STATE_CONFLICT, "已归档或已取消的巡检任务只读");
        }
        return task;
    }

    public void responsible(Subject subject) {
        InspectionException.require(subject.userId() != null && subject.userId() > 0, "请选择责任人");
        users.validateUser(subject.userId());
        if (!permissions.hasAnyPermissions(subject.userId(), subject.permission())) forbidden();
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(subject.context().tenantId())
                .subjectUserId(subject.userId()).anchorProjectId(subject.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (scope == null || !scope.fullProjectIds().contains(subject.projectId())) forbidden();
    }

    public void permission(InspectionRequestContext context, String permission) {
        if (permission == null || !permissions.hasAnyPermissions(context.actorId(), permission)) forbidden();
    }

    private static void forbidden() {
        throw new InspectionException(InspectionException.Reason.FORBIDDEN, "没有该巡检操作或资料范围的权限");
    }
}
