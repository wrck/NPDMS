package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.checklist;

import java.util.List;
import java.util.Map;

public final class CutoverChecklistReqVO {

    private CutoverChecklistReqVO() {
    }

    public record SelectedDefinition(Long itemDefinitionId, Integer itemDefinitionVersion) {
    }

    public record Generate(Integer expectedTaskVersion, Integer expectedAssessmentVersion,
                           Long expectedProjectScopeVersion,
                           Map<String, SelectedDefinition> selectedConflictDefinitions) {
    }

    public record Rematch(Integer expectedTaskVersion, Integer expectedAssessmentVersion,
                          Long expectedProjectScopeVersion, Long checklistId,
                          Integer expectedChecklistVersion, String expectedInputSnapshotHash,
                          Map<String, SelectedDefinition> selectedConflictDefinitions) {
    }

    public record DirectAnswer(String stableItemKey, String answerSnapshot) {
    }

    public record Save(Integer expectedTaskVersion, Long expectedProjectScopeVersion,
                       Long checklistId, Integer expectedChecklistVersion,
                       List<DirectAnswer> answers) {
    }

    public record CustomItem(Integer expectedTaskVersion, Long expectedProjectScopeVersion,
                             Long checklistId, Integer expectedChecklistVersion,
                             String itemTypeCode, String itemName, String itemDescription,
                             String interfaceFormatCode, String interfaceSchema,
                             Boolean required, String answerSnapshot) {
    }

    public record FileFactVersion(Integer artifactVersion, Integer referenceVersion,
                                  Integer availabilityVersion) {
    }

    public record FileHandle(Long artifactId, Integer versionNo, String referenceKey,
                             FileFactVersion fileFactVersion, Long scopeVersion) {
    }

    public record ManualResult(Integer expectedTaskVersion, Long expectedProjectScopeVersion,
                               Long checklistId, Integer expectedChecklistVersion,
                               FileHandle file, String factDescription) {
    }

    public record Submit(Integer expectedTaskVersion, Integer expectedAssessmentVersion,
                         Long expectedProjectScopeVersion, Long checklistId,
                         Integer expectedChecklistVersion) {
    }
}
