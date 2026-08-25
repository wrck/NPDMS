package cn.iocoder.yudao.module.pms.project.service.taskworkbench.command;

import java.time.LocalDateTime;

public final class ProjectTaskCommands {

    private ProjectTaskCommands() {
    }

    public record CreateTaskCommand(Long projectId, String taskCode, String name, String stageCode,
                                    Long parentTaskId, String businessLevelCode,
                                    LocalDateTime planStartTime, LocalDateTime planEndTime,
                                    Integer priority, Integer sortOrder, String description,
                                    String idempotencyKey, String requestDigest) {
    }

    public record UpdateTaskCommand(Long taskId, Integer expectedTaskVersion, String name,
                                    String businessLevelCode, LocalDateTime planStartTime,
                                    LocalDateTime planEndTime, Integer priority, Integer sortOrder,
                                    String description) {
    }

    public record MoveTaskCommand(Long taskId, Integer expectedTaskVersion, Long targetParentTaskId,
                                  Long expectedTaskTreeVersion, String reason,
                                  String idempotencyKey, String requestDigest) {
    }

    public record AddDependencyCommand(Long taskId, Integer expectedTaskVersion,
                                       Long predecessorTaskId, String dependencyTypeCode,
                                       String idempotencyKey, String requestDigest) {
    }
}
