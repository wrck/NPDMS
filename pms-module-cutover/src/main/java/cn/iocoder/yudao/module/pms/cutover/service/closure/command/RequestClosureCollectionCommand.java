package cn.iocoder.yudao.module.pms.cutover.service.closure.command;

import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.CollectionStage;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.Authentication;

public record RequestClosureCollectionCommand(Long tenantId, Long actorId, Long taskId,
                                              Long expectedTaskVersion, Long closureId,
                                              Long expectedClosureVersion, Long deviceId,
                                              CollectionStage collectionStage, Authentication authentication,
                                              String templateCode, Long templateVersion,
                                              String idempotencyKey, String correlationId) {
}
