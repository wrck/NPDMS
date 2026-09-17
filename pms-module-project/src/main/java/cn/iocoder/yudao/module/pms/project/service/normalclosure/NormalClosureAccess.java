package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectParticipantFactLockQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

@Component @RequiredArgsConstructor
public class NormalClosureAccess implements cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureContextApi {
    public static final String QUERY = "pms:acc-project-closure:query";
    public static final String SUBMIT = "pms:acc-project-closure:submit";
    public static final String AUDIT = "pms:acc-project-closure:audit";
    private final ProjectMasterMapper projects;
    private final ProjectMemberAssignmentMapper members;
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure.ClosureProjectMapper closureProjects;
    private final ProjectScopeApi scopes;
    private final ProjectParticipantFactApi participants;
    private final PermissionApi permissions;
    public record Actor(Long tenantId, Long userId, String correlationId) {}
    public record Context(ProjectMasterDO project, Long treeVersion) {}

    public Context read(Long projectId, Actor actor, String permission) {
        requireActor(actor, permission);
        var project = projects.selectById(projectId);
        requireProject(project, actor);
        boolean queryOnly = QUERY.equals(permission);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(), actor.userId(), projectId,
                queryOnly ? ProjectScopeApi.ACTION_VIEW : ProjectScopeApi.ACTION_MANAGE));
        if (scope == null || scope.treeVersion() == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(projectId)) throw failure("CLOSURE_PROJECT_SCOPE_REQUIRED");
        if (queryOnly) return new Context(project, scope.treeVersion());
        if (!Objects.equals(project.getManagerId(), actor.userId()))
            throw failure("CLOSURE_PM_MANAGE_REQUIRED");
        var pm = participants.inspect(new ProjectParticipantFactQuery(projectId, actor.userId(),
                Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), LocalDateTime.now()));
        if (pm == null || !Objects.equals(pm.userId(), actor.userId())) throw failure("CLOSURE_PM_MANAGE_REQUIRED");
        return new Context(project, scope.treeVersion());
    }

    public Context lock(Long projectId, Integer expectedVersion, Long expectedTreeVersion, Actor actor) {
        var project = lockProject(projectId, actor.tenantId());
        if (!Objects.equals(project.getVersion(), expectedVersion)) throw failure("CLOSURE_PROJECT_VERSION_CONFLICT");
        var scope = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(), actor.userId(), projectId,
                ProjectScopeApi.ACTION_MANAGE, expectedTreeVersion));
        if (!Objects.equals(scope.treeVersion(), expectedTreeVersion) || !scope.fullProjectIds().contains(projectId))
            throw failure("CLOSURE_TREE_VERSION_CONFLICT");
        var pm = members.selectParticipantFactsForUpdate(new ProjectParticipantFactLockQuery(actor.tenantId(), projectId,
                actor.userId(), Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));
        if (pm.size() != 1 || !Objects.equals(project.getManagerId(), actor.userId()))
            throw failure("CLOSURE_PM_MANAGE_REQUIRED");
        return new Context(project, scope.treeVersion());
    }

    /** Root first, then project; same serialization point as member/tree/task writers. */
    public ProjectMasterDO lockProject(Long projectId, Long tenantId) {
        var before = projects.selectById(projectId);
        if (before == null || !Objects.equals(before.getTenantId(), tenantId)) throw failure("CLOSURE_PROJECT_NOT_FOUND");
        Long rootId = before.getRootId() == null ? projectId : before.getRootId();
        var root = projects.selectByIdForUpdate(rootId);
        if (root == null || !Objects.equals(root.getTenantId(), tenantId)) throw failure("CLOSURE_PROJECT_NOT_FOUND");
        var project = Objects.equals(rootId, projectId) ? root : projects.selectByIdForUpdate(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)
                || !Objects.equals(rootId, project.getRootId() == null ? projectId : project.getRootId()))
            throw failure("CLOSURE_TREE_VERSION_CONFLICT");
        return project;
    }
    private void requireActor(Actor actor, String permission) {
        if (actor == null || actor.userId() == null || actor.tenantId() == null
                || !Objects.equals(actor.tenantId(), TenantContextHolder.getRequiredTenantId())
                || !permissions.hasAnyPermissions(actor.userId(), permission)) throw failure("CLOSURE_PERMISSION_DENIED");
    }
    private void requireProject(ProjectMasterDO project, Actor actor) {
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())) throw failure("CLOSURE_PROJECT_NOT_FOUND");
    }

    // ===== ProjectClosureContextApi 实现（跨模块平铺上下文；供 ACC 正常闭环消费）=====

    @Override
    public ClosureContext read(Long tenantId, Long projectId, Long actorId, String permission) {
        var context = read(projectId, new Actor(tenantId, actorId, null), permission);
        return toClosureContext(context);
    }

    @Override
    public ClosureContext lock(Long tenantId, Long projectId, Integer expectedProjectVersion,
                                Long expectedTreeVersion, Long actorId) {
        var context = lock(projectId, expectedProjectVersion, expectedTreeVersion, new Actor(tenantId, actorId, null));
        return toClosureContext(context);
    }

    @Override
    public java.util.List<Long> lockPrimaryServiceManagerUserIds(Long tenantId, Long projectId) {
        return closureProjects.selectPrimaryServiceManagersForUpdate(
                        new cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure.ClosureProjectMapper.ProjectQuery(tenantId, projectId))
                .stream().map(cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO::getUserId).toList();
    }

    private ClosureContext toClosureContext(Context context) {
        var project = context.project();
        return new ClosureContext(project.getId(), project.getTenantId(), project.getVersion(),
                project.getLifecycleStatus(), project.getCurrentStage(), project.getManagerId(), project.getRootId(),
                context.treeVersion(), project.getClosurePolicySnapshot(), project.getTaskTreeVersion(),
                project.getTaskProgressVersion(), project.getLifecycleTemplateId(), project.getLifecycleTemplateRevisionNo());
    }

    private static cn.iocoder.yudao.framework.common.exception.ServiceException failure(String reason) {
        return new cn.iocoder.yudao.framework.common.exception.ServiceException(
                cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_PROJECT_CLOSURE_VALIDATION_FAILED.getCode(), reason);
    }
}
