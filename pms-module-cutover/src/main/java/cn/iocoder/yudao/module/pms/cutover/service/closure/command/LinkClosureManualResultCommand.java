package cn.iocoder.yudao.module.pms.cutover.service.closure.command;

import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.AttachmentInput;

public record LinkClosureManualResultCommand(Long tenantId, Long actorId, Long taskId,
                                             Integer expectedTaskVersion, Long closureId,
                                             Integer expectedClosureVersion, String failedCollectionTaskId,
                                             AttachmentInput attachment, String idempotencyKey,
                                             String correlationId) {
}
