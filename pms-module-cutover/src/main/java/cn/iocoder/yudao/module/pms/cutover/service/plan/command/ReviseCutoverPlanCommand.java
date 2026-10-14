package cn.iocoder.yudao.module.pms.cutover.service.plan.command;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.CutoverTaskMutation;

public record ReviseCutoverPlanCommand(Long tenantId, Long actorId, Long taskId, Long expectedTaskVersion,
                                       Long sourcePlanRevisionId, String reason, String idempotencyKey,
                                       String correlationId) implements CutoverTaskMutation {
}
