package cn.iocoder.yudao.module.bpm.service.cutover;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.cutover.CutoverWorkflowApi;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Supplier;

/** 通用BPM办理页不能跳过CUT评审表；冻结身份与可编辑流程变量分开保存。 */
@Component("cutoverBpmGuard")
@RequiredArgsConstructor
public class CutoverBpmGuard {
    static final String PREFIX = "cutover:";
    static final String CURRENT = "cutoverCurrent:";
    private final RuntimeService runtime;
    private final CutoverRoleTaskAccess roleAccess;
    private final ThreadLocal<CutoverWorkflowApi.Start> starting = new ThreadLocal<>();
    private final ThreadLocal<Operation> operation = new ThreadLocal<>();
    record Operation(String instance, String node, Long actor, String action) {}

    <T> T start(CutoverWorkflowApi.Start command, Supplier<T> action) {
        if (starting.get() != null) throw new IllegalStateException("Nested CUT workflow start");
        starting.set(command);
        try { return action.get(); } finally { starting.remove(); }
    }
    void mutate(Operation value, Runnable action) {
        if (operation.get() != null) throw new IllegalStateException("Nested CUT workflow action");
        operation.set(value);
        try { action.run(); } finally { operation.remove(); }
    }
    public void freeze(DelegateExecution execution) {
        var command = starting.get();
        if (command == null || !command.tenantId().toString().equals(execution.getTenantId())
                || !command.approvalId().toString().equals(execution.getProcessInstanceBusinessKey())) {
            throw new IllegalStateException("割接审批必须从CUT已校验的方案入口发起");
        }
        String id = execution.getProcessInstanceId();
        runtime.addUserIdentityLink(id, command.actorId().toString(), PREFIX + "starter");
        runtime.addUserIdentityLink(id, command.taskId().toString(), PREFIX + "task");
        runtime.addGroupIdentityLink(id, command.grade(), PREFIX + "grade");
        for (var node : command.nodes()) {
            if (node.role() != null) {
                runtime.addGroupIdentityLink(id, node.role().groupKey(), PREFIX + "role:" + node.code());
                continue;
            }
            if (node.userId() == null) continue;
            runtime.addUserIdentityLink(id, node.userId().toString(), PREFIX + node.code());
            runtime.addUserIdentityLink(id, node.userId().toString(), CURRENT + node.code());
        }
    }
    public void validate(DelegateTask task) {
        requireTenant(Long.valueOf(task.getTenantId()));
        var links = runtime.getIdentityLinksForProcessInstance(task.getProcessInstanceId());
        var grade = links.stream().filter(link -> (PREFIX + "grade").equals(link.getType())).toList();
        if (grade.size() != 1 || !Objects.equals(grade.getFirst().getGroupId(), task.getVariable("cutoverGrade"))) {
            throw new IllegalStateException("割接审批等级与冻结路由不一致");
        }
        var candidates = links.stream().filter(link -> (CURRENT + task.getTaskDefinitionKey()).equals(link.getType())).toList();
        var role = links.stream().filter(link -> (PREFIX + "role:" + task.getTaskDefinitionKey()).equals(link.getType())).toList();
        boolean byRole = !role.isEmpty();
        if (byRole) {
            String code = task.getTaskDefinitionKey();
            String expectedRole = "SECOND_LINE".equals(code) ? "probSupporter" : "RND".equals(code) ? "probRD" : null;
            String group = role.size() == 1 ? role.getFirst().getGroupId() : null;
            var actual = task.getCandidates();
            if (role.size() != 1 || expectedRole == null || !candidates.isEmpty()
                    || !Objects.equals(expectedRole, task.getVariable("cutoverRoleCode_" + code))
                    || !Objects.equals(group, task.getVariable("cutoverGroup_" + code))
                    || !Objects.equals(group, "ROLE:" + task.getVariable("cutoverRoleId_" + code))
                    || actual.size() != 1 || !Objects.equals(actual.iterator().next().getGroupId(), group)
                    || actual.iterator().next().getUserId() != null)
                throw new IllegalStateException("割接角色待办不匹配业务冻结角色引用");
            if (task.getAssignee() != null && !("delete".equals(task.getEventName())
                    && operation.get() != null && "CANCEL".equals(operation.get().action()))) {
                var allowed = operation.get();
                if (allowed == null || !Objects.equals(allowed.instance(), task.getProcessInstanceId())
                        || !Objects.equals(allowed.node(), code) || !Objects.equals(allowed.actor().toString(), task.getAssignee())
                        || !List.of("APPROVE", "REJECT").contains(allowed.action())
                        || !roleAccess.lockEligible(new CutoverRoleTaskAccess.Qualification(
                                new cn.iocoder.yudao.module.system.api.permission.RoleCandidateFactApi.ActorQuery(
                                        Long.valueOf(task.getTenantId()), allowed.actor()), code, group)))
                    throw new IllegalStateException("割接角色待办仅允许当前有效成员在业务决定事务领取");
            }
        } else if (candidates.size() != 1 || !Objects.equals(candidates.getFirst().getUserId(), task.getAssignee())) {
            throw new IllegalStateException("割接审批人不匹配业务冻结候选人");
        }
        if (List.of("complete", "delete", "assignment").contains(task.getEventName())) {
            var allowed = operation.get();
            // 初次创建时引擎设置审批人；此时只能处于受控的start或上一节点决定中。
            if ("assignment".equals(task.getEventName()) && starting.get() != null) return;
            if (allowed == null || !Objects.equals(allowed.instance(), task.getProcessInstanceId())) {
                throw new IllegalStateException("请从割接详情填写完整评审并办理，通用BPM操作不能替代割接业务校验");
            }
            if (byRole && "assignment".equals(task.getEventName())
                    && (!List.of("APPROVE", "REJECT").contains(allowed.action())
                    || !Objects.equals(allowed.node(), task.getTaskDefinitionKey())
                    || !Objects.equals(allowed.actor().toString(), task.getAssignee())))
                throw new IllegalStateException("角色待办只能在割接业务校验后由办理人领取");
            if ("complete".equals(task.getEventName()) && (!"APPROVE".equals(allowed.action())
                    || !Objects.equals(allowed.node(), task.getTaskDefinitionKey()) || !Objects.equals(allowed.actor().toString(), task.getAssignee())
                    || !Objects.equals(task.getVariableLocal(BpmnVariableConstants.TASK_VARIABLE_STATUS), BpmTaskStatusEnum.APPROVE.getStatus()))) {
                throw new IllegalStateException("割接审批决定未通过业务入口验证");
            }
        }
    }
    static void requireTenant(Long tenant) {
        if (tenant == null || !Objects.equals(tenant, TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore()) throw new SecurityException("割接审批租户不一致");
    }
}
