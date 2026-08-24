package cn.iocoder.yudao.module.pms.project.service.projectmanual.command;

/** 正式手工创建项目结果及实例数量快照。 */
public record ManualProjectCreateResult(
        Long id,
        String projectCode,
        String status,
        String lifecycleStatus,
        String currentStage,
        String assignmentStatus,
        Integer version,
        Long lifecycleTemplateId,
        Integer lifecycleTemplateRevisionNo,
        String templateLoadMethod,
        Integer stageCount,
        Integer taskCount,
        Integer milestoneCount,
        Integer deliverableCount,
        Integer gateCount,
        Boolean serviceManagerAssigned,
        String matchResult,
        String matchDecisionMode,
        String matchOperationId) {
}
