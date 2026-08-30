package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import java.util.Map;

public record RematchChecklistCommand(Long tenantId, Long actorId, Long taskId,
                                      Integer expectedTaskVersion, Integer expectedAssessmentVersion,
                                      Long checklistId, Integer expectedChecklistVersion,
                                      String expectedInputSnapshotHash, Long expectedProjectScopeVersion,
                                      Map<String, GenerateChecklistCommand.SelectedDefinition> selectedConflictDefinitions,
                                      String idempotencyKey, String correlationId) {
    public RematchChecklistCommand {
        selectedConflictDefinitions = selectedConflictDefinitions == null
                ? Map.of() : Map.copyOf(selectedConflictDefinitions);
    }
}
