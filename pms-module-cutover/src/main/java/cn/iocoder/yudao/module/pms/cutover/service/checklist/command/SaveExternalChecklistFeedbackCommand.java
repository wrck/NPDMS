package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import java.util.List;

public record SaveExternalChecklistFeedbackCommand(Long tenantId, Long actorId, Long taskId,
        Long expectedTaskVersion, Long checklistId, Long expectedChecklistVersion,
        Long expectedProjectScopeVersion, String stableItemKey, Long expectedResultId,
        List<Feedback> feedback,
        @com.fasterxml.jackson.annotation.JsonIgnore cn.iocoder.yudao.module.pms.cutover.service.support.CutoverWriteIntent intent) implements CutoverChecklistDraftMutation {
    public record Feedback(Long sourceBulletinRowId, Boolean involved, String measures) { }
}
