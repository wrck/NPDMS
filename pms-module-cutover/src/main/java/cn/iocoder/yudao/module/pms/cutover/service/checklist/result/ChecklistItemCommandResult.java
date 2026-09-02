package cn.iocoder.yudao.module.pms.cutover.service.checklist.result;

public record ChecklistItemCommandResult(Long checklistId, Integer checklistVersion,
                                         Long checklistItemId, String stableItemKey,
                                         Integer resultVersion) {
}
