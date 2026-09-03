package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

public record RemoveCustomItemCommand(Long tenantId, Long actorId, Long taskId,
                                      Integer expectedTaskVersion, Long checklistId,
                                      Integer expectedChecklistVersion, Long expectedProjectScopeVersion,
                                      String stableItemKey) {
}
