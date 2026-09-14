package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

public record ProjectTaskExecutionRecord(
        Long projectId, Integer projectVersion, String projectStatus,
        Long taskId, Integer taskVersion, String taskStatus,
        Long executionContractId, Integer contractVersion, Long planVersionId,
        Long executionId, Integer executionVersion, Integer roundNo, String executionStatus,
        Long stageExecutionId, Integer stageExecutionVersion, String stageExecutionStatus, String stageStatus,
        java.time.LocalDateTime startedAt) { }
