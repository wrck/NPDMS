package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query;

public record CutoverChecklistDraftTouchUpdate(Long tenantId, Long checklistId, Integer expectedVersion) {
}
