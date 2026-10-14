package cn.iocoder.yudao.module.pms.cutover.service.closure.command;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.CutoverTaskMutation;

public record SubmitCutoverClosureCommand(Long tenantId, Long actorId, Long taskId,
                                          Long expectedTaskVersion, Long closureId,
                                          Long expectedClosureVersion, String finalResult,
                                          String idempotencyKey, String correlationId) implements CutoverTaskMutation {
}
