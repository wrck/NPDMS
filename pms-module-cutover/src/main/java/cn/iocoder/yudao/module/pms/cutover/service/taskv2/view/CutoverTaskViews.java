package cn.iocoder.yudao.module.pms.cutover.service.taskv2.view;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverReadinessPort;

import java.time.LocalDateTime;
import java.util.List;

public final class CutoverTaskViews {

    private CutoverTaskViews() {
    }

    public record CreateContextCandidate(CutoverProjectContextPort.ProjectContextFact project,
                                         List<CutoverDeviceScopePort.DeviceFact> devices,
                                         CutoverCustomerLevelPort.CustomerLevelFact customerServiceLevel,
                                         CutoverReadinessPort.ReadinessFact implementationReadiness,
                                         boolean createAllowed) {
    }

    public record CreateContextData(List<CreateContextCandidate> candidates, boolean selectionRequired) {
    }

    public record Summary(Long id, String taskNo, String taskName, String taskOrigin, String intakeSourceType,
                          Long projectId, String projectName, String officeCode, String officeName,
                          Long ownerUserId, String currentStage, String taskStatus, String manualGrade,
                          LocalDateTime scheduledTime, LocalDateTime generatedAt, int version) {
    }

    public record TaskCore(Long id, String taskNo, String taskName, String background, String taskOrigin,
                           String cutoverType, String networkMode, Long projectId, String projectName,
                           Long ownerUserId, String currentStage, String taskStatus, String manualGrade,
                           LocalDateTime scheduledTime, LocalDateTime createTime, int version) {
    }

    public record Source(String intakeSourceType, String sourceSystem, String sourceBusinessNo,
                         String businessEventId, Long legacyTaskId) {
    }

    public record Project(Long projectId, String projectCode, String projectName, Long projectScopeVersion) {
    }

    public record Assessment(Long id, int assessmentVersion, int rowVersion, String status,
                             String questionnaireTemplateCode, long questionnaireTemplateVersion,
                             CutoverAssessmentAnswers answers,
                             CutoverCustomerLevelPort.CustomerLevelFact customerServiceLevel,
                             String manualGrade, boolean simpleFlow, Long submittedBy,
                             LocalDateTime submittedAt, LocalDateTime invalidatedAt,
                             String invalidationReason) {
    }

    public record WorkbenchStep(String stage, String label, String state,
                                boolean isCurrent, boolean isAccessible) {
    }

    public record Detail(TaskCore task, Source source, Project project,
                         List<CutoverDeviceScopePort.DeviceFact> devices,
                         CutoverCustomerLevelPort.CustomerLevelFact customerServiceLevel,
                         CutoverReadinessPort.ReadinessFact implementationReadiness,
                         Assessment assessment, List<WorkbenchStep> workbenchSteps,
                         List<String> allowedActions) {
    }

    public record StoredAssessmentContext(CutoverProjectContextPort.ProjectContextFact project,
                                          List<CutoverDeviceScopePort.DeviceFact> devices,
                                          CutoverReadinessPort.ReadinessFact implementationReadiness,
                                          CutoverCustomerLevelPort.CustomerLevelFact customerServiceLevel) {
    }
}
