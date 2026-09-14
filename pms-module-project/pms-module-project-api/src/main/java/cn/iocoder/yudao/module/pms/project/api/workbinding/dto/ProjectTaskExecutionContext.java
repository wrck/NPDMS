package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** A versioned observation, not an authorization grant. Persist executionId with business-round evidence. */
public record ProjectTaskExecutionContext(
        Long projectId, Integer projectVersion, Long taskId, Integer taskVersion,
        Long executionContractId, Integer contractVersion, Long planVersionId,
        Long executionId, Integer executionVersion, Integer roundNo,
        Long stageExecutionId, Integer stageExecutionVersion, boolean writable, java.time.LocalDateTime startedAt) {
    public ProjectTaskExecutionQuery query() {
        return new ProjectTaskExecutionQuery(projectId, taskId, executionContractId);
    }
}
