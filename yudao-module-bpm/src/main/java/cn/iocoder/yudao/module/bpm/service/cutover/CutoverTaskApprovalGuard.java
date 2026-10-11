package cn.iocoder.yudao.module.bpm.service.cutover;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionQuery;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.*;

/** Copies the existing approval identity-link pattern; project membership remains owned by PROJ APIs. */
@Component("cutoverTaskApprovalGuard")
@RequiredArgsConstructor
public class CutoverTaskApprovalGuard {
    private static final String PREFIX = "cutoverTask:";
    private static final String REVIEWER = "reviewer";
    private static final List<String> IDENTITY = List.of(VAR_PROJECT, VAR_TASK, VAR_EXECUTION, VAR_CONTRACT);
    private final RuntimeService runtime;
    private final ProjectNodeExecutionApi executions;
    private final ProjectParticipantFactApi participants;
    private final ProjectScopeApi scopes;
    private final AdminUserApi users;

    public void freeze(DelegateExecution execution) {
        Long tenant = tenant(execution.getTenantId());
        Long project = id(execution.getVariable(VAR_PROJECT));
        Long task = id(execution.getVariable(VAR_TASK));
        Long round = id(execution.getVariable(VAR_EXECUTION));
        Long contract = id(execution.getVariable(VAR_CONTRACT));
        if (!Objects.equals(tenant, execution.getVariable(VAR_TENANT))
                || !Objects.equals(execution.getProcessDefinitionId(), execution.getVariable(VAR_DEFINITION))
                || !Objects.equals(NodeKind.TASK.businessKey(round), execution.getProcessInstanceBusinessKey()))
            throw new IllegalArgumentException("割接审批必须从当前项目任务入口发起");
        var current = executions.lockAndRevalidate(executions.inspect(new ProjectTaskExecutionQuery(project, task, contract)));
        if (!Objects.equals(round, current.executionId()) || current.startedAt() == null)
            throw new IllegalArgumentException("割接审批的任务执行轮次已失效");
        Long reviewer = authorize(tenant, project, null);
        for (String key : IDENTITY)
            runtime.addUserIdentityLink(execution.getProcessInstanceId(), id(execution.getVariable(key)).toString(), PREFIX + key);
        runtime.addUserIdentityLink(execution.getProcessInstanceId(), reviewer.toString(), PREFIX + REVIEWER);
    }

    public Set<Long> reviewer(DelegateExecution execution) {
        tenant(execution.getTenantId());
        return Set.of(frozen(execution.getProcessInstanceId(), REVIEWER));
    }

    /** Creation/decision listeners use identities outside editable process form variables. */
    public void validate(DelegateTask task) {
        boolean completing = "complete".equals(task.getEventName());
        boolean rejecting = "delete".equals(task.getEventName()) && Objects.equals(BpmTaskStatusEnum.REJECT.getStatus(),
                task.getVariableLocal(BpmnVariableConstants.TASK_VARIABLE_STATUS));
        if ("delete".equals(task.getEventName()) && !rejecting) return;
        Long tenant = tenant(task.getTenantId());
        for (String key : IDENTITY)
            if (!Objects.equals(frozen(task.getProcessInstanceId(), key), task.getVariable(key)))
                throw new IllegalArgumentException("割接审批的项目任务身份不可修改");
        if (!Objects.equals(tenant, task.getVariable(VAR_TENANT))
                || !Objects.equals(task.getProcessDefinitionId(), task.getVariable(VAR_DEFINITION)))
            throw new IllegalArgumentException("割接审批的租户或定义身份不可修改");
        Long reviewer = frozen(task.getProcessInstanceId(), REVIEWER);
        if (!Objects.equals(reviewer.toString(), task.getAssignee()))
            throw new IllegalArgumentException("割接审批人必须是发起时确定的本项目服务经理");
        if (completing && !Objects.equals(BpmTaskStatusEnum.APPROVE.getStatus(),
                task.getVariableLocal(BpmnVariableConstants.TASK_VARIABLE_STATUS)))
            throw new IllegalStateException("割接审批需要真实的人工审批决定");
        if (completing || rejecting)
            authorize(tenant, frozen(task.getProcessInstanceId(), VAR_PROJECT), reviewer);
    }

    private Long authorize(Long tenant, Long project, Long expectedReviewer) {
        var member = participants.inspect(new ProjectParticipantFactQuery(project, null,
                ProjectMemberRoles.SERVICE_CODES, LocalDateTime.now()));
        if (member == null || member.userId() == null
                || expectedReviewer != null && !Objects.equals(expectedReviewer, member.userId()))
            throw new IllegalArgumentException("本项目未配置唯一有效服务经理，或服务经理已变更");
        Long reviewer = member.userId();
        users.validateUser(reviewer);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant, reviewer, project, ProjectScopeApi.ACTION_VIEW));
        if (scope == null || !scope.fullProjectIds().contains(project))
            throw new IllegalArgumentException("服务经理没有本项目的访问权限");
        var locked = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant, reviewer, project,
                ProjectScopeApi.ACTION_VIEW, scope.treeVersion()));
        if (locked == null || !Objects.equals(scope.treeVersion(), locked.treeVersion())
                || !locked.fullProjectIds().contains(project))
            throw new IllegalArgumentException("服务经理的项目权限已变化");
        participants.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(project, reviewer,
                member.projectVersion(), "ACTIVE", null, ProjectMemberRoles.SERVICE_CODES));
        return reviewer;
    }

    private Long frozen(String instanceId, String key) {
        var links = runtime.getIdentityLinksForProcessInstance(instanceId).stream()
                .filter(link -> (PREFIX + key).equals(link.getType())).toList();
        if (links.size() != 1) throw new IllegalStateException("割接审批的项目或审批人快照缺失");
        return Long.valueOf(links.getFirst().getUserId());
    }

    private Long tenant(String value) {
        Long tenant = Long.valueOf(value);
        if (!Objects.equals(tenant, TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())
            throw new IllegalArgumentException("割接审批租户与当前上下文不一致");
        return tenant;
    }

    private Long id(Object value) {
        if (!(value instanceof Long number) || number <= 0)
            throw new IllegalArgumentException("割接审批缺少可信的项目任务身份");
        return number;
    }
}
