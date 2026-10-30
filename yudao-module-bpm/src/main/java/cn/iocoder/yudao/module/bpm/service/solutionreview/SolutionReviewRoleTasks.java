package cn.iocoder.yudao.module.bpm.service.solutionreview;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.bpm.api.solutionreview.SolutionReviewBpmApi;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleCandidateFactApi;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.module.bpm.api.solutionreview.SolutionReviewBpmApi.*;
import static cn.iocoder.yudao.module.bpm.service.solutionreview.SolutionReviewBpmGuard.*;

/** A role reference and actual decider are persisted; the group's members are always current SYSTEM facts. */
@Service
@RequiredArgsConstructor
public class SolutionReviewRoleTasks {
    static final String ROLE_PREFIX = PREFIX + "role:";
    static final String DECIDER_PREFIX = PREFIX + "decider:";
    private final RoleCandidateFactApi roles;
    private final ExplicitPermissionApi permissions;
    private final RuntimeService runtime;
    private final TaskService tasks;

    RoleCandidateFactApi.RoleReference reference(Long tenant) {
        requireTenant(tenant);
        var role = roles.inspectRole(new RoleCandidateFactApi.Query(tenant, ENGINEERING_REVIEW_ROLE));
        if (role == null) throw new IllegalArgumentException("工程管理部角色未配置或已停用");
        return role;
    }

    public List<String> groups(RoleCandidateFactApi.ActorQuery query) {
        requireTenant(query.tenantId());
        if (!permissions.hasExplicitPermission(query.tenantId(), query.actorUserId(), ENGINEERING_REVIEW_PERMISSION))
            return List.of();
        return roles.inspectActor(query).stream()
                .filter(member -> Objects.equals(member.userId(), query.actorUserId()) && ENGINEERING_REVIEW_ROLE.equals(member.roleCode()))
                .map(member -> "ROLE:" + member.roleId()).distinct().toList();
    }

    record Qualification(RoleCandidateFactApi.ActorQuery actor, String groupKey) { }
    private RoleCandidateFactApi.ActorMembership qualify(Qualification query, boolean lock) {
        requireTenant(query.actor().tenantId());
        var members = lock ? roles.lockAndInspectActor(query.actor()) : roles.inspectActor(query.actor());
        var member = members.stream().filter(candidate -> Objects.equals(candidate.userId(), query.actor().actorUserId())
                && ENGINEERING_REVIEW_ROLE.equals(candidate.roleCode()) && ("ROLE:" + candidate.roleId()).equals(query.groupKey()))
                .findFirst().orElse(null);
        if (member == null) return null;
        boolean granted = lock ? permissions.lockAndCheck(query.actor().tenantId(), query.actor().actorUserId(), ENGINEERING_REVIEW_PERMISSION)
                : permissions.hasExplicitPermission(query.actor().tenantId(), query.actor().actorUserId(), ENGINEERING_REVIEW_PERMISSION);
        return granted ? member : null;
    }

    String frozenGroup(Task task) {
        var links = runtime.getIdentityLinksForProcessInstance(task.getProcessInstanceId()).stream()
                .filter(link -> (ROLE_PREFIX + task.getTaskDefinitionKey()).equals(link.getType())).toList();
        if (links.isEmpty()) return null;
        if (links.size() != 1 || links.getFirst().getUserId() != null || links.getFirst().getGroupId() == null)
            throw new IllegalStateException("方案复审角色引用不完整");
        return links.getFirst().getGroupId();
    }

    private boolean currentRoleTask(Task task, Long tenant, String group) {
        var candidates = tasks.getIdentityLinksForTask(task.getId()).stream()
                .filter(link -> "candidate".equals(link.getType())).toList();
        return task != null && !task.isSuspended() && tenant.toString().equals(task.getTenantId())
                && runtime.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId())
                .processInstanceTenantId(tenant.toString()).processDefinitionKey(DEFINITION_KEY).active().count() == 1
                && candidates.size() == 1 && candidates.getFirst().getUserId() == null
                && group.equals(candidates.getFirst().getGroupId());
    }

    public SolutionReviewBpmApi.ReadAccess read(SolutionReviewBpmApi.ReadQuery query) {
        requireTenant(query.tenantId());
        if (query.actorId() == null || !Objects.equals(query.actorId(), SecurityFrameworkUtils.getLoginUserId())
                || query.businessKey() == null) return null;
        var instance = runtime.createProcessInstanceQuery().processInstanceTenantId(query.tenantId().toString())
                .processDefinitionKey(DEFINITION_KEY).processInstanceBusinessKey(query.businessKey()).active().singleResult();
        if (instance == null) return null;
        for (var task : tasks.createTaskQuery().processInstanceId(instance.getId()).taskTenantId(query.tenantId().toString()).active().list()) {
            String group = frozenGroup(task);
            if (group != null && (task.getAssignee() == null || query.actorId().toString().equals(task.getAssignee()))
                    && currentRoleTask(task, query.tenantId(), group)
                    && qualify(new Qualification(new RoleCandidateFactApi.ActorQuery(query.tenantId(), query.actorId()), group), false) != null)
                return new ReadAccess(instance.getId(), task.getId(), task.getTaskDefinitionKey(), instance.getBusinessKey());
        }
        return null;
    }

    public record DecisionActor(Long tenantId, Long actorId, String taskId) { }
    public void validateApprovalVariables(cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO request) {
        if (request.getVariables() == null || request.getVariables().isEmpty()) return;
        var task = tasks.createTaskQuery().taskId(request.getId()).taskTenantId(
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId().toString()).singleResult();
        if (task == null || runtime.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId())
                .processDefinitionKey(DEFINITION_KEY).count() != 1) return;
        request.getVariables().forEach((key, value) -> {
            if (key.startsWith("solutionReview") || "projectId".equals(key)) {
                Object actual = tasks.getVariableLocal(task.getId(), key);
                if (actual == null) actual = runtime.getVariable(task.getProcessInstanceId(), key);
                if (actual == null || !Objects.deepEquals(actual, value))
                    throw new IllegalArgumentException("方案审核身份、级别和资格审计字段不得由客户端覆盖");
            }
        });
    }
    public boolean canReadTask(DecisionActor query) {
        requireTenant(query.tenantId());
        var task = tasks.createTaskQuery().taskId(query.taskId()).taskTenantId(query.tenantId().toString()).singleResult();
        if (task == null) return false;
        var process = runtime.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId())
                .processInstanceTenantId(query.tenantId().toString()).processDefinitionKey(DEFINITION_KEY).active().singleResult();
        if (process == null) return false;
        var access = read(new ReadQuery(query.tenantId(), query.actorId(), process.getBusinessKey()));
        return access != null && query.taskId().equals(access.taskId());
    }
    /** Called only by the normal approve/reject commands, inside their transaction. */
    public void claimForDecision(DecisionActor query) {
        requireTenant(query.tenantId());
        var task = tasks.createTaskQuery().taskId(query.taskId()).taskTenantId(query.tenantId().toString()).singleResult();
        if (task == null) return;
        String group = frozenGroup(task);
        if (group == null) return; // Immutable personal instances keep their original contract.
        if (query.actorId() == null || !Objects.equals(query.actorId(), SecurityFrameworkUtils.getLoginUserId())
                || task.getAssignee() != null && !query.actorId().toString().equals(task.getAssignee())
                || !currentRoleTask(task, query.tenantId(), group)) throw new IllegalArgumentException("当前方案复审待办不属于本人");
        var member = qualify(new Qualification(new RoleCandidateFactApi.ActorQuery(query.tenantId(), query.actorId()), group), true);
        if (member == null) throw new IllegalArgumentException("当前用户已不具备工程管理部复审资格");
        tasks.setVariableLocal(task.getId(), "solutionReviewRoleId", member.roleId());
        tasks.setVariableLocal(task.getId(), "solutionReviewMembershipWatermark", member.membershipWatermark());
        tasks.setVariableLocal(task.getId(), "solutionReviewUserWatermark", member.userWatermark());
        if (task.getAssignee() == null) {
            runtime.addUserIdentityLink(task.getProcessInstanceId(), query.actorId().toString(), DECIDER_PREFIX + task.getTaskDefinitionKey());
            tasks.claim(task.getId(), query.actorId().toString());
        }
    }

    void validate(org.flowable.task.service.delegate.DelegateTask task) {
        Long tenant = Long.valueOf(task.getTenantId());
        requireTenant(tenant);
        var frozen = runtime.getIdentityLinksForProcessInstance(task.getProcessInstanceId());
        var roles = frozen.stream().filter(link -> (ROLE_PREFIX + task.getTaskDefinitionKey()).equals(link.getType())).toList();
        if (roles.size() != 1) throw new IllegalStateException("方案复审角色引用不完整");
        String group = roles.getFirst().getGroupId();
        var candidates = task.getCandidates();
        if (group == null || candidates.size() != 1 || !group.equals(candidates.iterator().next().getGroupId())
                || candidates.iterator().next().getUserId() != null) throw new IllegalStateException("方案复审待办与冻结角色不一致");
        if ("create".equals(task.getEventName()) && task.getAssignee() == null) return;
        boolean rejecting = "delete".equals(task.getEventName()) && Objects.equals(task.getVariableLocal(
                cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.TASK_VARIABLE_STATUS),
                cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum.REJECT.getStatus());
        if ("delete".equals(task.getEventName()) && !rejecting) return;
        var deciders = frozen.stream().filter(link -> (DECIDER_PREFIX + task.getTaskDefinitionKey()).equals(link.getType())).toList();
        if (deciders.size() != 1 || task.getAssignee() == null || !task.getAssignee().equals(deciders.getFirst().getUserId()))
            throw new IllegalStateException("角色复审只能由实际领取成员办理");
        if ("complete".equals(task.getEventName()) || "assignment".equals(task.getEventName()) || rejecting) {
            var member = qualify(new Qualification(new RoleCandidateFactApi.ActorQuery(tenant, Long.valueOf(task.getAssignee())), group), true);
            if (member == null) throw new IllegalArgumentException("当前用户已不具备工程管理部复审资格");
            if (!Objects.equals(member.roleId(), task.getVariableLocal("solutionReviewRoleId"))
                    || !Objects.equals(member.membershipWatermark(), task.getVariableLocal("solutionReviewMembershipWatermark"))
                    || !Objects.equals(member.userWatermark(), task.getVariableLocal("solutionReviewUserWatermark")))
                throw new IllegalStateException("方案复审资格审计不匹配实际办理资格");
        }
    }
}
