package cn.iocoder.yudao.module.pms.cutover.service.closure.command;

import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.AttachmentInput;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.CollectionStage;

public record LinkClosureManualResultCommand(Long tenantId, Long actorId, Long taskId,
                                             Integer expectedTaskVersion, Long closureId,
                                             Integer expectedClosureVersion, String failedCollectionTaskId,
                                             Long deviceId, CollectionStage collectionStage,
                                             AttachmentInput attachment, String idempotencyKey,
                                             String correlationId) {
}
