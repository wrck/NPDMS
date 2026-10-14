package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import java.util.List;
import java.util.Map;

public record GenerateChecklistCommand(Long tenantId, Long actorId, Long taskId,
                                       Long expectedTaskVersion, Long expectedAssessmentVersion,
                                       Long expectedProjectScopeVersion,
                                       List<String> deviceTypeCodes,
                                       Map<String, SelectedDefinition> selectedConflictDefinitions,
                                       String idempotencyKey, String correlationId) implements CutoverChecklistMatchMutation {
    public GenerateChecklistCommand {
        deviceTypeCodes = deviceTypeCodes == null ? List.of() : List.copyOf(deviceTypeCodes);
        selectedConflictDefinitions = selectedConflictDefinitions == null
                ? Map.of() : Map.copyOf(selectedConflictDefinitions);
    }

    public record SelectedDefinition(Long itemDefinitionId, Integer itemDefinitionVersion) {
    }
}
