package cn.iocoder.yudao.module.pms.cutover.service.checklist.result;

public record ChecklistItemCommandResult(Long checklistId, Long checklistVersion,
                                         Long checklistItemId, String stableItemKey,
                                         Integer resultVersion) {
}
