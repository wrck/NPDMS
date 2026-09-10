package cn.iocoder.yudao.module.bpm.service.normalclosure;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi.StartCommand;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Freezes candidates outside editable form variables, using BPM's own identity-link history. */
@Component("bpmNormalClosureGuard")
@RequiredArgsConstructor
public class BpmNormalClosureGuard {
    static final String MANAGER_NODE = "serviceManagerReview";
    static final String MATERIAL_NODE = "materialReview";
    static final String MANAGER_VARIABLE = "serviceManagerUserId";
    static final String MATERIAL_VARIABLE = "materialReviewerUserId";
    static final String LINK_PREFIX = "normalClosure:";
    private final RuntimeService runtimeService;
    private final AdminUserApi adminUserApi;
    private final ExplicitPermissionApi explicitPermissionApi;
    private final ThreadLocal<StartCommand> authorizedStart = new ThreadLocal<>();

    <T> T withAuthorizedStart(StartCommand command, Supplier<T> action) {
        if (authorizedStart.get() != null) {
            throw new IllegalStateException("Nested NORMAL closure start is not supported");
        }
        authorizedStart.set(command);
        try {
            return action.get();
        } finally {
            authorizedStart.remove();
        }
    }

    public void freeze(DelegateExecution execution) {
        StartCommand command = authorizedStart.get();
        if (command == null || !Objects.equals(command.tenantId().toString(), execution.getTenantId())
                || !Objects.equals(command.businessKey(), execution.getProcessInstanceBusinessKey())) {
            throw new IllegalStateException("NORMAL closure must start through its authorized business API");
        }
        runtimeService.addUserIdentityLink(execution.getProcessInstanceId(), command.serviceManagerUserId().toString(),
                LINK_PREFIX + MANAGER_NODE);
        runtimeService.addUserIdentityLink(execution.getProcessInstanceId(), command.materialReviewerUserId().toString(),
                LINK_PREFIX + MATERIAL_NODE);
    }

    /** Invoked on create and complete; form variables must never replace the frozen candidates. */
    public void validate(DelegateTask task) {
        requireTenant(Long.valueOf(task.getTenantId()));
        var links = runtimeService.getIdentityLinksForProcessInstance(task.getProcessInstanceId());
        for (String node : List.of(MANAGER_NODE, MATERIAL_NODE)) {
            var frozen = links.stream().filter(link -> (LINK_PREFIX + node).equals(link.getType())).toList();
            if (frozen.size() != 1) {
                throw new IllegalStateException("Missing frozen NORMAL candidate: " + node);
            }
            String user = frozen.getFirst().getUserId();
            String variable = MANAGER_NODE.equals(node) ? MANAGER_VARIABLE : MATERIAL_VARIABLE;
            if (!Objects.equals(user, String.valueOf(task.getVariable(variable)))) {
                throw new IllegalStateException("NORMAL candidate variables are immutable");
            }
            if (node.equals(task.getTaskDefinitionKey()) && !Objects.equals(user, task.getAssignee())) {
                throw new IllegalStateException("NORMAL task assignee differs from frozen candidate");
            }
        }
        boolean completing = "complete".equals(task.getEventName());
        boolean rejecting = "delete".equals(task.getEventName()) && Objects.equals(BpmTaskStatusEnum.REJECT.getStatus(),
                task.getVariableLocal(BpmnVariableConstants.TASK_VARIABLE_STATUS));
        if (completing || rejecting) {
            if (completing && !Objects.equals(BpmTaskStatusEnum.APPROVE.getStatus(),
                    task.getVariableLocal(BpmnVariableConstants.TASK_VARIABLE_STATUS))) {
                throw new IllegalStateException("NORMAL task requires a real BPM approval");
            }
            adminUserApi.validateUser(Long.valueOf(task.getAssignee()));
            if (MATERIAL_NODE.equals(task.getTaskDefinitionKey()) && !explicitPermissionApi.lockAndCheck(
                    Long.valueOf(task.getTenantId()), Long.valueOf(task.getAssignee()),
                    BpmNormalClosureService.MATERIAL_PERMISSION)) {
                throw new IllegalStateException("Material reviewer explicit audit grant is no longer valid");
            }
        }
    }

    static void requireTenant(Long tenantId) {
        if (tenantId == null || !Objects.equals(tenantId, TenantContextHolder.getTenantId())
                || TenantContextHolder.isIgnore()) {
            throw new IllegalArgumentException("NORMAL closure tenant does not match the authenticated context");
        }
    }
}
