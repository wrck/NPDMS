package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

public record AddCustomItemCommand(Long tenantId, Long actorId, Long taskId,
                                   Long expectedTaskVersion, Long checklistId,
                                   Long expectedChecklistVersion, Long expectedProjectScopeVersion,
                                   String itemTypeCode, String itemName, String itemDescription,
                                   String interfaceFormatCode, String interfaceSchema,
                                   boolean required, String answerSnapshot) {
}
