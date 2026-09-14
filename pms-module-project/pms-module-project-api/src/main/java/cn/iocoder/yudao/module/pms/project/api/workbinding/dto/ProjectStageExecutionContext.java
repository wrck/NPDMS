package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** A versioned observation, not an authorization grant. Owners retain permission, scope and business-state checks. */
public record ProjectStageExecutionContext(
        Long projectId, Integer projectVersion, Long stageId, Integer stageVersion,
        Long executionContractId, Integer contractVersion, Long planVersionId,
        Long executionId, Integer executionVersion, Integer roundNo, boolean writable) {
    public ProjectStageExecutionQuery query() {
        return new ProjectStageExecutionQuery(projectId, stageId, executionContractId);
    }
}
