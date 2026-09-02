package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query;

public record CutoverChecklistItemApplicabilityUpdate(Long tenantId, Long itemId,
                                                      boolean applicable, boolean required,
                                                      Long itemDefinitionId, Integer itemDefinitionVersion,
                                                      String itemTypeCode, String itemName,
                                                      String itemDescription, String interfaceFormatCode,
                                                      String interfaceSchemaSnapshot, String displayConditionSnapshot,
                                                      String workModeCode,
                                                      Long matchedRuleId, Integer matchedRuleVersion,
                                                      Integer sortOrder,
                                                      Long actorId) {
}
