package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.domain.rule.NormalClosureTaskCondition;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Prospective closure readiness; never changes the task or its persisted completion evidence. */
public record NormalClosureTaskProjection(Long taskId, String taskCode, Long contractId) {
    public record Query(Long tenantId, String currentStage, List<ProjectTaskInstanceDO> tasks,
                        Map<Long, ProjectTaskExecutionContractDO> contracts) { }

    public static NormalClosureTaskProjection resolve(Query query) {
        var matches = query.tasks().stream().filter(task -> {
            var contract = query.contracts().get(task.getId());
            return NormalClosureTaskCondition.matches(contract);
        }).toList();
        if (matches.isEmpty()) return null;
        if (matches.size() != 1) throw invalid();
        var task = matches.getFirst();
        var contract = query.contracts().get(task.getId());
        if ("DONE".equals(task.getStatus())) return null;
        if (!Objects.equals(task.getTenantId(), query.tenantId())
                || !Objects.equals(task.getStageCode(), query.currentStage())
                || !Objects.equals(contract.getTenantId(), query.tenantId())
                || !Objects.equals(contract.getProjectTaskId(), task.getId())
                || contract.getEffectiveTo() != null || contract.getSourceNodeKey() == null
                || !Set.of("IN_PROGRESS", "PENDING_ACCEPT").contains(String.valueOf(task.getStatus()))) throw invalid();
        return new NormalClosureTaskProjection(task.getId(), task.getCode(), contract.getId());
    }

    public boolean includes(ProjectTaskInstanceDO task) {
        return Objects.equals(taskId, task.getId()) && Objects.equals(taskCode, task.getCode());
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("CLOSURE_COMPLETION_TASK_INVALID");
    }
}
