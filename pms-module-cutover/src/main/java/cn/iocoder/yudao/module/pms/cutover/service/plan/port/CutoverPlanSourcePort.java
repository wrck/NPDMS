package cn.iocoder.yudao.module.pms.cutover.service.plan.port;

import java.util.List;
import java.util.HashSet;
import java.util.Comparator;

import static cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanRules.*;

public interface CutoverPlanSourcePort {
    SourceFacts inspect(Long tenantId, Long actorId, Long taskId);
    SourceFacts lockAndRevalidate(Long tenantId, Long actorId, SourceFacts expected);

    record SourceFacts(SourceSnapshot snapshot, List<RiskFactSnapshot> failedRiskFacts) {
        public SourceFacts {
            require(snapshot != null, "sourceSnapshot");
            require(failedRiskFacts != null, "failedRiskFacts");
            failedRiskFacts = failedRiskFacts.stream()
                    .sorted(Comparator.comparing(RiskFactSnapshot::stableItemKey)).toList();
            require(new HashSet<>(failedRiskFacts.stream().map(RiskFactSnapshot::stableItemKey).toList()).size()
                    == failedRiskFacts.size(), "failedRiskFacts.stableItemKey");
            require(!"D".equals(snapshot.grade()) || failedRiskFacts.isEmpty(), "failedRiskFacts.grade");
            require(snapshot.failedRiskFacts().equals(failedRiskFacts), "failedRiskFacts.snapshot");
        }
    }

    record SourceSnapshot(Integer snapshotVersion, Long taskId, Integer taskVersion,
                          Long assessmentId, Integer assessmentVersion, String grade,
                          Long checklistId, Integer checklistVersion, Long projectId,
                          Integer projectVersion, Long projectScopeVersion,
                          List<DeviceSnapshot> devices, Long configurationRevisionId,
                          String configurationCode, Integer configurationRevisionNo,
                          List<TemplateSectionSnapshot> templateSections,
                          List<RiskFactSnapshot> failedRiskFacts) {
        public SourceSnapshot {
            require(snapshotVersion != null && snapshotVersion > 0, "snapshotVersion");
            require(taskId != null && taskId > 0 && taskVersion != null && taskVersion >= 0, "task");
            require(assessmentId != null && assessmentId > 0 && assessmentVersion != null && assessmentVersion > 0, "assessment");
            require(List.of("A", "B", "C", "D").contains(grade), "grade");
            require(("D".equals(grade) && checklistId == null && checklistVersion == null)
                    || (!"D".equals(grade) && checklistId != null && checklistId > 0
                    && checklistVersion != null && checklistVersion > 0), "checklist");
            require(projectId != null && projectId > 0 && projectVersion != null && projectVersion >= 0
                    && projectScopeVersion != null && projectScopeVersion >= 0, "project");
            require(configurationRevisionId != null && configurationRevisionId > 0
                    && configurationCode != null && !configurationCode.isBlank()
                    && configurationCode.equals(configurationCode.trim()) && configurationCode.length() <= 64
                    && configurationRevisionNo != null && configurationRevisionNo > 0, "configuration");
            require(devices != null && !devices.isEmpty(), "devices");
            devices = devices.stream().sorted(Comparator.comparing(DeviceSnapshot::deviceId)).toList();
            require(new HashSet<>(devices.stream().map(DeviceSnapshot::deviceId).toList()).size() == devices.size(), "deviceId");
            require(new HashSet<>(devices.stream().map(d -> comparisonKey(d.serialNumber())).toList()).size() == devices.size(), "serialNumber");
            require(templateSections != null && !templateSections.isEmpty(), "templateSections");
            templateSections = templateSections.stream().sorted(Comparator.comparing(TemplateSectionSnapshot::sortOrder)
                    .thenComparing(TemplateSectionSnapshot::stableSectionKey)).toList();
            require(new HashSet<>(templateSections.stream().map(TemplateSectionSnapshot::stableSectionKey).toList()).size()
                    == templateSections.size(), "stableSectionKey");
            if ("D".equals(grade)) require(templateSections.size() == SIMPLE_SECTIONS.size()
                    && new HashSet<>(templateSections.stream().map(TemplateSectionSnapshot::stableSectionKey).toList())
                    .equals(new HashSet<>(SIMPLE_SECTIONS)), "D templateSections");
            require(failedRiskFacts != null, "failedRiskFacts");
            failedRiskFacts = failedRiskFacts.stream()
                    .sorted(Comparator.comparing(RiskFactSnapshot::stableItemKey)).toList();
            require(new HashSet<>(failedRiskFacts.stream().map(RiskFactSnapshot::stableItemKey).toList()).size()
                    == failedRiskFacts.size(), "failedRiskFacts.stableItemKey");
            require(!"D".equals(grade) || failedRiskFacts.isEmpty(), "failedRiskFacts.grade");
        }
    }
    record DeviceSnapshot(Long deviceId, String serialNumber, Long projectAssignmentVersion,
                          String deviceTypeCode, String deviceTypeSourceVersion) {
        public DeviceSnapshot {
            require(deviceId != null && deviceId > 0, "deviceId");
            comparisonKey(serialNumber);
            require(serialNumber.length() <= 128, "serialNumber");
            require(projectAssignmentVersion != null && projectAssignmentVersion >= 0, "projectAssignmentVersion");
            requireText(deviceTypeCode, 64, "deviceTypeCode");
            requireText(deviceTypeSourceVersion, 128, "deviceTypeSourceVersion");
        }
    }
    record TemplateSectionSnapshot(String stableSectionKey, String title, Integer sortOrder,
                                   List<String> cutoverTypeCodes, List<String> levelCodes, Boolean required) {
        public TemplateSectionSnapshot {
            requireText(stableSectionKey, 64, "stableSectionKey");
            requireText(title, 128, "title");
            require(sortOrder != null && sortOrder >= 0 && required != null, "templateSection");
            require(cutoverTypeCodes != null && !cutoverTypeCodes.isEmpty(), "cutoverTypeCodes");
            cutoverTypeCodes.forEach(v -> requireText(v, 64, "cutoverTypeCode"));
            require(new HashSet<>(cutoverTypeCodes).size() == cutoverTypeCodes.size(), "cutoverTypeCodes");
            cutoverTypeCodes = cutoverTypeCodes.stream().sorted().toList();
            require(levelCodes != null && !levelCodes.isEmpty()
                    && levelCodes.stream().allMatch(List.of("A", "B", "C", "D")::contains), "levelCodes");
            require(new HashSet<>(levelCodes).size() == levelCodes.size(), "levelCodes");
            levelCodes = List.of("A", "B", "C", "D").stream().filter(levelCodes::contains).toList();
        }
    }

    record RiskFactSnapshot(Long checklistItemId, String stableItemKey, Integer itemResultVersion,
                            String itemName, String resultCode, String factDescription) {
        public RiskFactSnapshot {
            require(checklistItemId != null && checklistItemId > 0, "checklistItemId");
            requireText(stableItemKey, 128, "stableItemKey");
            require(itemResultVersion != null && itemResultVersion > 0, "itemResultVersion");
            requireText(itemName, 255, "itemName");
            require(List.of("FAILED", "NO", "NOT_PASSED").contains(resultCode), "resultCode");
            requireText(factDescription, 4000, "factDescription");
        }
    }

    private static void requireText(String value, int max, String field) {
        require(value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= max, field);
    }
}
