package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.checklist;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistExportException;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

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

    public record CustomItemRemove(Integer expectedTaskVersion, Long expectedProjectScopeVersion,
                                   Long checklistId, Integer expectedChecklistVersion) {
    }

    public record CollectionRequest(Integer expectedTaskVersion, Long expectedProjectScopeVersion,
                                    Long checklistId, Integer expectedChecklistVersion,
                                    Long deviceId, Long commandTemplateId) {
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

    public static final class Export {
        private Integer checklistVersion;
        private boolean checklistVersionSpecified;

        public Export() {
        }

        public Export(Integer checklistVersion) {
            setChecklistVersion(checklistVersion);
        }

        public Integer checklistVersion() {
            return checklistVersion;
        }

        @JsonSetter("checklistVersion")
        public void setChecklistVersion(Integer checklistVersion) {
            this.checklistVersion = checklistVersion;
            this.checklistVersionSpecified = true;
        }

        @JsonIgnore
        public boolean isChecklistVersionSpecified() {
            return checklistVersionSpecified;
        }

        @JsonAnySetter
        public void rejectUnknown(String key, Object value) {
            throw new CutoverChecklistExportException(
                    CutoverChecklistExportException.Code.INVALID_EXPORT_REQUEST, "导出请求包含未知字段：" + key);
        }
    }
}
