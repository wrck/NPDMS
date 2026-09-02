package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import java.util.Map;

public record GenerateChecklistCommand(Long tenantId, Long actorId, Long taskId,
                                       Integer expectedTaskVersion, Integer expectedAssessmentVersion,
                                       Long expectedProjectScopeVersion,
                                       Map<String, SelectedDefinition> selectedConflictDefinitions,
                                       String idempotencyKey, String correlationId) {
    public GenerateChecklistCommand {
        selectedConflictDefinitions = selectedConflictDefinitions == null
                ? Map.of() : Map.copyOf(selectedConflictDefinitions);
    }

    public record SelectedDefinition(Long itemDefinitionId, Integer itemDefinitionVersion) {
    }
}
