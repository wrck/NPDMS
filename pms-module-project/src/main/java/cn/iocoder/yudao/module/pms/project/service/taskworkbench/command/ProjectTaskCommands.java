package cn.iocoder.yudao.module.pms.project.service.taskworkbench.command;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

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
                                    String description, Set<String> submittedFields) {
        public UpdateTaskCommand {
            submittedFields = submittedFields == null ? null : Set.copyOf(submittedFields);
        }

        public UpdateTaskCommand(Long taskId, Integer expectedTaskVersion, String name,
                                 String businessLevelCode, LocalDateTime planStartTime,
                                 LocalDateTime planEndTime, Integer priority, Integer sortOrder,
                                 String description) {
            this(taskId, expectedTaskVersion, name, businessLevelCode, planStartTime, planEndTime,
                    priority, sortOrder, description, inferSubmittedFields(name, businessLevelCode,
                            planStartTime, planEndTime, priority, sortOrder, description));
        }

        private static Set<String> inferSubmittedFields(String name, String businessLevelCode,
                                                        LocalDateTime planStartTime, LocalDateTime planEndTime,
                                                        Integer priority, Integer sortOrder, String description) {
            Set<String> fields = new LinkedHashSet<>();
            if (name != null) fields.add("name");
            if (businessLevelCode != null) fields.add("businessLevelCode");
            if (planStartTime != null) fields.add("planStartTime");
            if (planEndTime != null) fields.add("planEndTime");
            if (priority != null) fields.add("priority");
            if (sortOrder != null) fields.add("sortOrder");
            if (description != null) fields.add("description");
            return Set.copyOf(fields);
        }
    }

    public record MoveTaskCommand(Long taskId, Integer expectedTaskVersion, Long targetParentTaskId,
                                  Long expectedTaskTreeVersion, String reason,
                                  String idempotencyKey, String requestDigest) {
    }

    public record AddDependencyCommand(Long taskId, Integer expectedTaskVersion,
                                       Long predecessorTaskId, String dependencyTypeCode,
                                       String idempotencyKey, String requestDigest) {
    }

    public record AssignTaskCommand(Long taskId, Integer expectedTaskVersion, Long assigneeUserId,
                                    String reason, String idempotencyKey, String requestDigest) {
    }
}
