package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

public record LoadExternalChecklistCommand(Long tenantId, Long actorId, Long taskId,
        Long expectedTaskVersion, Long checklistId, Long expectedChecklistVersion,
        Long expectedProjectScopeVersion, String stableItemKey) implements CutoverChecklistDraftMutation {
    public LoadExternalChecklistCommand afterReservation() {
        return new LoadExternalChecklistCommand(tenantId, actorId, taskId, expectedTaskVersion,
                checklistId, expectedChecklistVersion + 1, expectedProjectScopeVersion, stableItemKey);
    }
}
