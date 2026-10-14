package cn.iocoder.yudao.module.bpm.service.cutover;

import cn.iocoder.yudao.module.bpm.api.cutover.CutoverWorkflowApi;
import cn.iocoder.yudao.module.system.api.permission.RoleCandidateFactApi;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.TaskQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** SYSTEM当前有效成员投影到Flowable原生候选组，既不注册整组用户也不持久化人员名单。 */
@Service
@RequiredArgsConstructor
public class CutoverRoleTaskAccess {
    private final RoleCandidateFactApi roles;
    private final TaskService tasks;

    public List<String> groups(RoleCandidateFactApi.ActorQuery query) {
        CutoverBpmGuard.requireTenant(query.tenantId());
        return roles.inspectActor(query).stream().filter(member -> Objects.equals(member.userId(), query.actorUserId())
                        && List.of("probSupporter", "probRD").contains(member.roleCode()))
                .map(member -> "ROLE:" + member.roleId()).distinct().toList();
    }

    public TaskQuery todos(RoleCandidateFactApi.ActorQuery query) {
        var tasksForActor = tasks.createTaskQuery().taskTenantId(query.tenantId().toString())
                .taskCandidateOrAssigned(query.actorUserId().toString());
        var groups = groups(query);
        if (!groups.isEmpty()) tasksForActor.taskCandidateGroupIn(groups);
        return tasksForActor;
    }

    public record TaskActor(RoleCandidateFactApi.ActorQuery actor, String taskId) { }
    public boolean candidate(TaskActor query) {
        var tasksForActor = tasks.createTaskQuery().taskId(query.taskId()).taskTenantId(query.actor().tenantId().toString())
                .taskUnassigned().taskCandidateUser(query.actor().actorUserId().toString());
        var groups = groups(query.actor());
        if (!groups.isEmpty()) tasksForActor.taskCandidateGroupIn(groups);
        return tasksForActor.count() == 1;
    }

    public record Qualification(RoleCandidateFactApi.ActorQuery actor, String nodeCode, String groupKey) { }
    public boolean lockEligible(Qualification query) {
        CutoverBpmGuard.requireTenant(query.actor().tenantId());
        String role = "SECOND_LINE".equals(query.nodeCode()) ? "probSupporter" : "RND".equals(query.nodeCode()) ? "probRD" : null;
        return role != null && roles.lockAndInspectActor(query.actor()).stream().anyMatch(member ->
                Objects.equals(member.userId(), query.actor().actorUserId()) && role.equals(member.roleCode())
                        && ("ROLE:" + member.roleId()).equals(query.groupKey()));
    }

    public record RoleQuery(Long tenantId, CutoverWorkflowApi.RoleReference role) { }
    public void requireRole(RoleQuery query) {
        CutoverBpmGuard.requireTenant(query.tenantId());
        var current = roles.inspectRole(new RoleCandidateFactApi.Query(query.tenantId(), query.role().roleCode()));
        if (current == null || !Objects.equals(current.roleId(), query.role().roleId()))
            throw new IllegalArgumentException("割接角色已停用或身份已变化");
    }
}
