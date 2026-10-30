package cn.iocoder.yudao.module.bpm.service.durationchange;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.durationchange.DurationChangeBpmApi;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.TASK_VARIABLE_REASON;

@Service
@RequiredArgsConstructor
public class DurationChangeBpmService implements DurationChangeBpmApi {

    private final HistoryService historyService;

    @Override
    public String getApprovalOpinion(ApprovalOpinionQuery query) {
        if (query == null || query.tenantId() == null
                || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())
                || query.processInstanceId() == null || query.processInstanceId().isBlank()
                || query.processDefinitionKey() == null || query.processDefinitionKey().isBlank()
                || query.businessKey() == null || query.businessKey().isBlank()
                || query.approverUserId() == null || query.approverUserId() <= 0) {
            throw new IllegalArgumentException("工期审批意见查询关联无效");
        }
        var process = historyService.createHistoricProcessInstanceQuery()
                .processInstanceTenantId(query.tenantId().toString())
                .processInstanceId(query.processInstanceId()).includeProcessVariables().singleResult();
        if (process == null || process.getEndTime() == null
                || !Objects.equals(query.processDefinitionKey(), process.getProcessDefinitionKey())
                || !Objects.equals(query.businessKey(), process.getBusinessKey())
                || !Objects.equals(BpmProcessInstanceStatusEnum.APPROVE.getStatus(),
                        FlowableUtils.getProcessInstanceStatus(process))) {
            throw new IllegalStateException("工期审批通过结果不存在或关联不匹配");
        }
        var approvedTasks = historyService.createHistoricTaskInstanceQuery()
                .taskTenantId(query.tenantId().toString()).processInstanceId(query.processInstanceId())
                .taskDefinitionKey("serviceManagerApprove").taskAssignee(query.approverUserId().toString())
                .finished().includeTaskLocalVariables().list().stream()
                .filter(task -> Objects.equals(BpmTaskStatusEnum.APPROVE.getStatus(),
                        FlowableUtils.getTaskStatus(task))).toList();
        if (approvedTasks.size() != 1) {
            throw new IllegalStateException("工期服务经理审批任务不存在或不唯一");
        }
        return (String) approvedTasks.getFirst().getTaskLocalVariables().get(TASK_VARIABLE_REASON);
    }
}
