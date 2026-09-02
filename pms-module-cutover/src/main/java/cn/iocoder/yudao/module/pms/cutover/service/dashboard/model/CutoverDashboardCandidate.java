package cn.iocoder.yudao.module.pms.cutover.service.dashboard.model;

/** CUT dashboard candidate identity and current task state. */
public record CutoverDashboardCandidate(Long taskId, String taskOrigin, String currentStage,
                                        String taskStatus, Long ownerUserId, Long actorId,
                                        String manualGrade) {
}
