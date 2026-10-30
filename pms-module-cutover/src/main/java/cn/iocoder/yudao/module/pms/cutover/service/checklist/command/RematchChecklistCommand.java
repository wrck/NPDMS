package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import java.util.Map;

@lombok.Builder
public record RematchChecklistCommand(Long tenantId, Long actorId, Long taskId,
                                      Long expectedTaskVersion, Long expectedAssessmentVersion,
                                      Long checklistId, Long expectedChecklistVersion,
                                      String expectedInputSnapshotHash, Long expectedProjectScopeVersion,
                                      CutoverChecklistMatchInput matchingInput,
                                      Map<String, GenerateChecklistCommand.SelectedDefinition> selectedConflictDefinitions,
                                      String idempotencyKey, String correlationId)
        implements CutoverChecklistDraftMutation, CutoverChecklistMatchMutation {
    public RematchChecklistCommand {
        selectedConflictDefinitions = selectedConflictDefinitions == null
                ? Map.of() : Map.copyOf(selectedConflictDefinitions);
    }
}
