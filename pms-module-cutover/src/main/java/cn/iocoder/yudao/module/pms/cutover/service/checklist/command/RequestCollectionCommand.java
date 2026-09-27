package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

public record RequestCollectionCommand(Long tenantId, Long actorId, Long taskId,
                                       Long expectedTaskVersion, Long checklistId,
                                       Long expectedChecklistVersion, Long expectedProjectScopeVersion,
                                       String stableItemKey, Long deviceId, Long commandTemplateId,
                                       String idempotencyKey, String correlationId) {
}
