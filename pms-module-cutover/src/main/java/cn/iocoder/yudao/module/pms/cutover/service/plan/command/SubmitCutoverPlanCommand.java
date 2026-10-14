package cn.iocoder.yudao.module.pms.cutover.service.plan.command;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.CutoverTaskMutation;

public record SubmitCutoverPlanCommand(Long tenantId, Long actorId, Long taskId,
                                       Long expectedTaskVersion, Long expectedPlanVersion,
                                       String idempotencyKey, String correlationId) implements CutoverTaskMutation {
}
