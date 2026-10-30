package cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan;

public record ProjectStageExecutionRecord(
        Long projectId, Long projectVersion, String projectStatus,
        Long stageId, Integer stageVersion, String stageStatus,
        Long executionContractId, Integer contractVersion, Long planVersionId,
        Long executionId, Integer executionVersion, Integer roundNo, String executionStatus) { }
