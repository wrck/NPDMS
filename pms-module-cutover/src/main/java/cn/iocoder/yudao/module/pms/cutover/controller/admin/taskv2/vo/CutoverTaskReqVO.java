package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo;

import java.time.LocalDateTime;
import java.util.List;

/** F-CUT-002 六路由请求模型。 */
public final class CutoverTaskReqVO {

    private CutoverTaskReqVO() {
    }

    public record ResolveCreateContext(List<String> serialNumbers) {
    }

    public record ProjectContext(Long projectId, Integer projectVersion, String projectCode, String projectName,
                                 Long customerId, String customerCode, String customerName,
                                 Long officeDepartmentId, String officeCode, String officeName) {
    }

    public record DeviceWatermark(Long deviceId, String serialNumber, Long projectAssignmentVersion) {
    }

    public record Create(Long projectId, String configurationCode, List<String> serialNumbers,
                         String taskName, String background, String cutoverType, String networkMode,
                         LocalDateTime scheduledTime, ProjectContext expectedProjectContext,
                         Long expectedProjectScopeVersion, List<DeviceWatermark> expectedDeviceScopeWatermark,
                         Long expectedReadinessSnapshotId, Long expectedReadinessSnapshotVersion,
                         String expectedCustomerServiceLevelStatus, Long expectedCustomerServiceLevelRevisionId,
                         String expectedCustomerServiceLevelCode, Long expectedCustomerServiceLevelFactVersion,
                         LocalDateTime expectedCustomerServiceLevelEffectiveFrom,
                         LocalDateTime expectedCustomerServiceLevelEffectiveTo) {
    }

    public record AssessmentAnswers(String businessImportanceLevel, String operationComplexityLevel,
                                    String hiddenRiskLevel, Boolean sparePartApplied) {
    }

    public record SaveAssessment(AssessmentAnswers answers, String manualGrade) {
    }
}
