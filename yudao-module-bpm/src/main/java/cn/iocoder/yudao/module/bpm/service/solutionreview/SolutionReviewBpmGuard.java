package cn.iocoder.yudao.module.bpm.service.solutionreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.solutionreview.SolutionReviewBpmApi;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/** Candidate identities are frozen outside editable process variables, as in the existing closure guard. */
@Component("solutionReviewBpmGuard")
@RequiredArgsConstructor
public class SolutionReviewBpmGuard {
    static final String PREFIX = "solutionReview:";
    private final RuntimeService runtime;
    private final RepositoryService repository;
    private final ProjectScopeApi scope;
    private final ProjectParticipantFactApi participants;
    private final ExplicitPermissionApi permissions;
    private final AdminUserApi users;
    private record Authorized(SolutionReviewBpmApi.Start command, Map<String, Long> candidates) { }
    private final ThreadLocal<Authorized> authorized = new ThreadLocal<>();

    <T> T starting(SolutionReviewBpmApi.Start command, Map<String, Long> candidates, Supplier<T> action) {
        if (authorized.get() != null) throw new IllegalStateException("Nested solution review");
        authorized.set(new Authorized(command, Map.copyOf(candidates)));
        try { return action.get(); } finally { authorized.remove(); }
    }

    public void freeze(DelegateExecution execution) {
        var frozen = authorized.get();
        if (frozen == null || !Objects.equals(frozen.command().tenantId().toString(), execution.getTenantId())
                || !Objects.equals(frozen.command().businessKey(), execution.getProcessInstanceBusinessKey())
                || !Objects.equals(frozen.command().definitionId(), execution.getProcessDefinitionId()))
            throw new IllegalStateException("方案审核必须从已授权方案入口发起");
        runtime.addUserIdentityLink(execution.getProcessInstanceId(), frozen.command().actorId().toString(), PREFIX + "starter");
        runtime.addUserIdentityLink(execution.getProcessInstanceId(), frozen.command().projectId().toString(), PREFIX + "project");
        frozen.candidates().forEach((key, user) -> runtime.addUserIdentityLink(
                execution.getProcessInstanceId(), user.toString(), PREFIX + key));
    }

    void authorize(Long tenant, Long actor, Long project, String responsibility) {
        requireTenant(tenant);
        if (actor == null) throw new IllegalArgumentException("审批人无效");
        users.validateUser(actor);
        var visible = scope.resolveCurrent(new ProjectCurrentScopeQuery(tenant, actor, project, ProjectScopeApi.ACTION_VIEW));
        if (visible == null || !visible.fullProjectIds().contains(project)) throw new IllegalArgumentException("审批人没有项目范围");
        var locked = scope.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant, actor, project,
                ProjectScopeApi.ACTION_VIEW, visible.treeVersion()));
        if (locked == null || !locked.fullProjectIds().contains(project)) throw new IllegalArgumentException("审批人项目范围已变化");
        if ("SERVICE_MANAGER".equals(responsibility)) {
            var member = participants.inspect(new ProjectParticipantFactQuery(project, actor,
                    ProjectMemberRoles.SERVICE_CODES, LocalDateTime.now()));
            if (member == null || member.effectiveRoleCodes().stream().noneMatch(ProjectMemberRoles.SERVICE_CODES::contains))
                throw new IllegalArgumentException("初审人必须是本项目有效服务经理");
            participants.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(project, actor,
                    member.projectVersion(), "ACTIVE", null, ProjectMemberRoles.SERVICE_CODES));
        } else if (!"ENGINEERING_MANAGEMENT".equals(responsibility)
                || !permissions.lockAndCheck(tenant, actor, SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION)) {
            throw new IllegalArgumentException("复审人必须具有工程管理部方案复审授权");
        }
    }

    public void validate(DelegateTask task) {
        Long tenant = Long.valueOf(task.getTenantId());
        requireTenant(tenant);
        var links = runtime.getIdentityLinksForProcessInstance(task.getProcessInstanceId());
        java.util.function.Function<String, Long> frozen = key -> {
            var found = links.stream().filter(link -> (PREFIX + key).equals(link.getType())).toList();
            if (found.size() != 1) throw new IllegalStateException("审批候选人或业务范围快照缺失");
            return Long.valueOf(found.getFirst().getUserId());
        };
        Long candidate = frozen.apply(task.getTaskDefinitionKey());
        if (!Objects.equals(candidate.toString(), task.getAssignee())) throw new IllegalStateException("审批人不匹配冻结候选人");
        boolean deciding = "complete".equals(task.getEventName()) || "delete".equals(task.getEventName())
                && Objects.equals(task.getVariableLocal(BpmnVariableConstants.TASK_VARIABLE_STATUS), BpmTaskStatusEnum.REJECT.getStatus());
        if ("complete".equals(task.getEventName()) && !Objects.equals(task.getVariableLocal(
                BpmnVariableConstants.TASK_VARIABLE_STATUS), BpmTaskStatusEnum.APPROVE.getStatus()))
            throw new IllegalStateException("缺少真实 BPM 审批决定");
        if (deciding) {
            var element = repository.getBpmnModel(task.getProcessDefinitionId()).getFlowElement(task.getTaskDefinitionKey());
            authorize(tenant, candidate, frozen.apply("project"), SolutionReviewBpmService.responsibility(element));
        }
    }

    /** 服务经理节点默认审批人：本项目当前唯一有效服务经理；缺失、多人或兼任冲突均拒绝提交。 */
    long resolveServiceManager(Long tenant, Long project) {
        requireTenant(tenant);
        try {
            return participants.inspect(new ProjectParticipantFactQuery(project, null,
                    ProjectMemberRoles.SERVICE_CODES, LocalDateTime.now())).userId();
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException failure) {
            throw new IllegalArgumentException("本项目未配置唯一有效服务经理，无法提交审批");
        }
    }

    /** 工程管理部节点默认审批人：major-review 显式授权的唯一持有人；未配置或多人均拒绝提交。 */
    long resolveEngineeringReviewer(Long tenant) {
        requireTenant(tenant);
        var holders = permissions.listUsersWithPermission(tenant, SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION);
        if (holders.isEmpty()) throw new IllegalArgumentException("工程管理部方案复审授权未配置持有人，请先在权限管理中授予");
        if (holders.size() > 1) throw new IllegalArgumentException("工程管理部方案复审授权存在多名持有人，无法确定默认复审人");
        return holders.getFirst();
    }

    static void requireTenant(Long tenant) {
        if (tenant == null || !Objects.equals(tenant, TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())
            throw new IllegalArgumentException("审批租户与当前上下文不一致");
    }
}
