package cn.iocoder.yudao.module.pms.cutover.controller.business.checklist.vo;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistExportException;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.List;
import java.util.Map;

public final class CutoverChecklistReqVO {

    private CutoverChecklistReqVO() {
    }

    public interface TaskVersioned { Long expectedTaskVersion(); }
    public interface DraftVersioned extends TaskVersioned { Long expectedChecklistVersion(); }

    public record SelectedDefinition(Long itemDefinitionId, Integer itemDefinitionVersion) {
    }

    public record Generate(Long expectedTaskVersion, Long expectedAssessmentVersion,
                           Long expectedProjectScopeVersion,
                           List<String> deviceTypeCodes,
                           Map<String, SelectedDefinition> selectedConflictDefinitions) implements TaskVersioned {
    }

    public record Rematch(Long expectedTaskVersion, Long expectedAssessmentVersion,
                          Long expectedProjectScopeVersion, Long checklistId,
                          Long expectedChecklistVersion, String expectedInputSnapshotHash,
                          List<String> deviceTypeCodes,
                          Map<String, SelectedDefinition> selectedConflictDefinitions) implements DraftVersioned {
    }

    public record DirectAnswer(String stableItemKey, String answerSnapshot) {
    }

    public record Save(Long expectedTaskVersion, Long expectedProjectScopeVersion,
                       Long checklistId, Long expectedChecklistVersion,
                       List<DirectAnswer> answers) implements DraftVersioned {
    }

    public record ExternalLoad(Long expectedTaskVersion, Long expectedProjectScopeVersion,
                               Long checklistId, Long expectedChecklistVersion) implements DraftVersioned { }

    public record DeviceVersions(Long expectedTaskVersion, Long expectedProjectScopeVersion,
                                 Long checklistId, Long expectedChecklistVersion,
                                 List<cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveChecklistDeviceVersionsCommand.Device> devices) implements DraftVersioned { }

    public record ExternalFeedback(Long expectedTaskVersion, Long expectedProjectScopeVersion,
                                   Long checklistId, Long expectedChecklistVersion, Long expectedResultId,
                                   List<cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveExternalChecklistFeedbackCommand.Feedback> feedback) implements DraftVersioned { }

    public record CustomItem(Long expectedTaskVersion, Long expectedProjectScopeVersion,
                             Long checklistId, Long expectedChecklistVersion,
                             String itemTypeCode, String itemName, String itemDescription,
                             String interfaceFormatCode, String interfaceSchema,
                             Boolean required, String answerSnapshot) implements DraftVersioned {
    }

    public record CustomItemRemove(Long expectedTaskVersion, Long expectedProjectScopeVersion,
                                   Long checklistId, Long expectedChecklistVersion) implements DraftVersioned {
    }

    public record CollectionRequest(Long expectedTaskVersion, Long expectedProjectScopeVersion,
                                    Long checklistId, Long expectedChecklistVersion,
                                    Long deviceId, Long commandTemplateId) implements DraftVersioned {
    }

    public record FileFactVersion(Integer artifactVersion, Integer referenceVersion,
                                  Integer availabilityVersion) {
    }

    public record FileHandle(Long artifactId, Integer versionNo, String referenceKey,
                             FileFactVersion fileFactVersion, Long scopeVersion) {
    }

    public record ManualResult(Long expectedTaskVersion, Long expectedProjectScopeVersion,
                               Long checklistId, Long expectedChecklistVersion,
                               FileHandle file, String factDescription) implements DraftVersioned {
    }

    public record Submit(Long expectedTaskVersion, Long expectedAssessmentVersion,
                         Long expectedProjectScopeVersion, Long checklistId,
                         Long expectedChecklistVersion) implements DraftVersioned {
    }

    public static final class Export {
        private Long checklistVersion;
        private boolean checklistVersionSpecified;

        public Export() {
        }

        public Export(Long checklistVersion) {
            setChecklistVersion(checklistVersion);
        }

        public Long checklistVersion() {
            return checklistVersion;
        }

        @JsonSetter("checklistVersion")
        public void setChecklistVersion(Long checklistVersion) {
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
