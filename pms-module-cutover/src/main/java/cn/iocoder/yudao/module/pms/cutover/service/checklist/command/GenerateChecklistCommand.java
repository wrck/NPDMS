package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import java.util.Map;

@lombok.Builder
public record GenerateChecklistCommand(Long tenantId, Long actorId, Long taskId,
                                       Long expectedTaskVersion, Long expectedAssessmentVersion,
                                       Long expectedProjectScopeVersion,
                                       CutoverChecklistMatchInput matchingInput,
                                       Map<String, SelectedDefinition> selectedConflictDefinitions,
                                       String idempotencyKey, String correlationId) implements CutoverChecklistMatchMutation {
    public GenerateChecklistCommand {
        selectedConflictDefinitions = selectedConflictDefinitions == null
                ? Map.of() : Map.copyOf(selectedConflictDefinitions);
    }

    public record SelectedDefinition(Long itemDefinitionId, Integer itemDefinitionVersion) {
    }
}
