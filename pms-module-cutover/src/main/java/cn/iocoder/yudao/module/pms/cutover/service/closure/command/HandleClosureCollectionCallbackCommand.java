package cn.iocoder.yudao.module.pms.cutover.service.closure.command;

import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.CollectionStage;

import java.time.LocalDateTime;

public record HandleClosureCollectionCallbackCommand(Long tenantId, Long taskId, Long closureId,
                                                     Long deviceId, CollectionStage collectionStage,
                                                     String callbackEventId, String collectionTaskId,
                                                     boolean succeeded, String resultRef, String resultVersion,
                                                     LocalDateTime occurredAt, String correlationId) {
}
