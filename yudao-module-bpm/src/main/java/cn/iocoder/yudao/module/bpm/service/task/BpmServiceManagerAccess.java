package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Service;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Constrains service-manager approval grants to participated processes and assigned tasks. */
@Service
@RequiredArgsConstructor
public class BpmServiceManagerAccess {
    private final PermissionApi permissions;
    private final HistoryService history;
    private final TaskService tasks;

    private boolean restricted() {
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        return actor != null && permissions.hasAnyRoles(actor, "SERVICE_MANAGER")
                && !permissions.hasAnyPermissions(actor, "bpm:task:manager-query");
    }

    public void requireProcess(String processInstanceId) {
        if (!restricted()) return;
        String actor = String.valueOf(SecurityFrameworkUtils.getLoginUserId());
        String tenant = String.valueOf(TenantContextHolder.getRequiredTenantId());
        var process = history.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId)
                .processInstanceTenantId(tenant).singleResult();
        if (process == null) throw exception(FORBIDDEN);
        if (actor.equals(process.getStartUserId())) return;
        if (history.createHistoricTaskInstanceQuery().processInstanceId(processInstanceId)
                .taskTenantId(tenant).taskAssignee(actor).count() > 0) return;
        if (history.createHistoricTaskInstanceQuery().processInstanceId(processInstanceId)
                .taskTenantId(tenant).taskOwner(actor).count() > 0) return;
        throw exception(FORBIDDEN);
    }

    public void requireTask(String taskId) {
        if (!restricted()) return;
        var task = tasks.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw exception(FORBIDDEN);
        requireProcess(task.getProcessInstanceId());
    }

    public void requireAssignedTask(String taskId) {
        if (!restricted()) return;
        var task = tasks.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || !Objects.equals(task.getTenantId(), String.valueOf(TenantContextHolder.getRequiredTenantId()))
                || !Objects.equals(task.getAssignee(), String.valueOf(SecurityFrameworkUtils.getLoginUserId()))) throw exception(FORBIDDEN);
    }

    /** The approval grant does not include reassignment, sign manipulation, copying or generic process creation. */
    public void requireAdvancedAction() {
        if (restricted()) throw exception(FORBIDDEN);
    }
}
