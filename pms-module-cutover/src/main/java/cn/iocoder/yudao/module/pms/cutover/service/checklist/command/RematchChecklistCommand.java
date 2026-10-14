package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

import java.util.List;
import java.util.Map;

public record RematchChecklistCommand(Long tenantId, Long actorId, Long taskId,
                                      Long expectedTaskVersion, Long expectedAssessmentVersion,
                                      Long checklistId, Long expectedChecklistVersion,
                                      String expectedInputSnapshotHash, Long expectedProjectScopeVersion,
                                      List<String> deviceTypeCodes,
                                      Map<String, GenerateChecklistCommand.SelectedDefinition> selectedConflictDefinitions,
                                      String idempotencyKey, String correlationId)
        implements CutoverChecklistDraftMutation, CutoverChecklistMatchMutation {
    public RematchChecklistCommand {
        deviceTypeCodes = deviceTypeCodes == null ? List.of() : List.copyOf(deviceTypeCodes);
        selectedConflictDefinitions = selectedConflictDefinitions == null
                ? Map.of() : Map.copyOf(selectedConflictDefinitions);
    }
}
