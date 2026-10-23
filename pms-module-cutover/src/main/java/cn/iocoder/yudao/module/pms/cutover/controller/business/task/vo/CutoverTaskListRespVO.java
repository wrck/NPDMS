package cn.iocoder.yudao.module.pms.cutover.controller.business.task.vo;

import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskListService;
import java.time.LocalDateTime;

/** 列表HTTP合同；审批受限摘要不包含普通任务的配置、部门和负责人事实。 */
public record CutoverTaskListRespVO(Long id, String taskNo, String taskName, String taskOrigin,
        String intakeSourceType, Long configurationRevisionId, String configurationCode, Integer configurationRevisionNo,
        Long projectId, String projectName, String departmentCode, String departmentName, Long ownerUserId,
        String currentStage, String taskStatus, String manualGrade, LocalDateTime scheduledTime,
        LocalDateTime generatedAt, Long version, boolean approvalOnly, String creatorName, Long approvalInstanceId) {
    public static CutoverTaskListRespVO from(CutoverTaskListService.Item item) {
        var task = item.task();
        return new CutoverTaskListRespVO(task.id(), task.taskNo(), task.taskName(), task.taskOrigin(),
                task.intakeSourceType(), task.approvalOnly() ? null : task.configurationRevisionId(),
                task.approvalOnly() ? null : task.configurationCode(), task.approvalOnly() ? null : task.configurationRevisionNo(),
                task.projectId(), task.projectName(), task.approvalOnly() ? null : task.departmentCode(),
                task.approvalOnly() ? null : task.departmentName(), task.approvalOnly() ? null : task.ownerUserId(),
                task.currentStage(), task.taskStatus(), task.manualGrade(), task.scheduledTime(), task.generatedAt(),
                task.version(), task.approvalOnly(), item.creatorName(), item.approvalInstanceId());
    }
}
