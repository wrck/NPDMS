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

/** BPM approval adapter; PROJ retains ownership of task rounds, participants and project scope. */
@Component("cutoverTaskApprovalGuard")
@RequiredArgsConstructor
public class CutoverTaskApprovalGuard {
    private static final String LINK = "cutoverTask:";
    private static final String REVIEWER = "reviewer";
    private static final List<String> IDENTITIES = List.of(VAR_PROJECT, VAR_TASK, VAR_EXECUTION, VAR_CONTRACT);
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
            throw new IllegalArgumentException("割接任务执行轮次已失效");
        Long reviewer = authorizedReviewer(tenant, project, null);
        for (String key : IDENTITIES)
            runtime.addUserIdentityLink(execution.getProcessInstanceId(), id(execution.getVariable(key)).toString(), LINK + key);
        runtime.addUserIdentityLink(execution.getProcessInstanceId(), reviewer.toString(), LINK + REVIEWER);
    }

    public Set<Long> reviewer(DelegateExecution execution) {
        tenant(execution.getTenantId());
        return Set.of(frozen(execution.getProcessInstanceId(), REVIEWER));
    }

    public void validate(DelegateTask task) {
        boolean completing = "complete".equals(task.getEventName());
        boolean rejecting = "delete".equals(task.getEventName()) && Objects.equals(BpmTaskStatusEnum.REJECT.getStatus(),
                task.getVariableLocal(BpmnVariableConstants.TASK_VARIABLE_STATUS));
        if ("delete".equals(task.getEventName()) && !rejecting) return;
        Long tenant = tenant(task.getTenantId());
        for (String key : IDENTITIES)
            if (!Objects.equals(frozen(task.getProcessInstanceId(), key), task.getVariable(key)))
                throw new IllegalArgumentException("割接审批的项目任务身份不可修改");
        if (!Objects.equals(tenant, task.getVariable(VAR_TENANT))
                || !Objects.equals(task.getProcessDefinitionId(), task.getVariable(VAR_DEFINITION)))
            throw new IllegalArgumentException("割接审批租户或流程定义已变化");
        Long reviewer = frozen(task.getProcessInstanceId(), REVIEWER);
        if (!Objects.equals(reviewer.toString(), task.getAssignee()))
            throw new IllegalArgumentException("割接审批人必须为发起时确定的本项目服务经理");
        if (completing && !Objects.equals(BpmTaskStatusEnum.APPROVE.getStatus(),
                task.getVariableLocal(BpmnVariableConstants.TASK_VARIABLE_STATUS)))
            throw new IllegalStateException("割接审批必须有真实的人工审批决定");
        if (completing || rejecting) authorizedReviewer(tenant, frozen(task.getProcessInstanceId(), VAR_PROJECT), reviewer);
    }

    private Long authorizedReviewer(Long tenant, Long project, Long expected) {
        var fact = participants.inspect(new ProjectParticipantFactQuery(project, null, ProjectMemberRoles.SERVICE_CODES, LocalDateTime.now()));
        if (fact == null || fact.userId() == null || expected != null && !Objects.equals(expected, fact.userId()))
            throw new IllegalArgumentException("项目必须配置唯一有效服务经理，且审批期间不能替换");
        Long reviewer = fact.userId();
        users.validateUser(reviewer);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant, reviewer, project, ProjectScopeApi.ACTION_VIEW));
        if (scope == null || !scope.fullProjectIds().contains(project)) throw new IllegalArgumentException("服务经理无项目访问权限");
        var locked = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant, reviewer, project, ProjectScopeApi.ACTION_VIEW, scope.treeVersion()));
        if (locked == null || !Objects.equals(scope.treeVersion(), locked.treeVersion()) || !locked.fullProjectIds().contains(project))
            throw new IllegalArgumentException("服务经理项目权限已变化");
        participants.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(project, reviewer, fact.projectVersion(), "ACTIVE", null, ProjectMemberRoles.SERVICE_CODES));
        return reviewer;
    }

    private Long frozen(String instance, String key) {
        var links = runtime.getIdentityLinksForProcessInstance(instance).stream().filter(link -> (LINK + key).equals(link.getType())).toList();
        if (links.size() != 1) throw new IllegalStateException("割接审批身份快照缺失");
        return Long.valueOf(links.getFirst().getUserId());
    }
    private Long tenant(String value) {
        Long tenant = Long.valueOf(value);
        if (TenantContextHolder.isIgnore() || !Objects.equals(tenant, TenantContextHolder.getTenantId())) throw new IllegalArgumentException("割接审批租户不匹配");
        return tenant;
    }
    private Long id(Object value) {
        if (!(value instanceof Long number) || number <= 0) throw new IllegalArgumentException("割接审批缺少可信项目任务身份");
        return number;
    }
}
