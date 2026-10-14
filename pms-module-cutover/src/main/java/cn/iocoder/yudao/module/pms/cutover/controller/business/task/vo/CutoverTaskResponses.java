package cn.iocoder.yudao.module.pms.cutover.controller.business.task.vo;

import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.result.CutoverAssessmentCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.task.result.CutoverTaskCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.task.view.CutoverTaskViews;
import java.time.LocalDateTime;
import java.util.List;

/** 明确的HTTP响应合同；字段保持现行业务投影，不暴露Service内部记录类型。 */
public final class CutoverTaskResponses {
    private CutoverTaskResponses() { }

    public record ConfigurationChoice(String configurationCode, String configurationName, Long revisionId,
            int revisionNo, LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
        public static ConfigurationChoice from(CutoverTaskViews.ConfigurationChoice value) {
            if (value == null) return null;
            return new ConfigurationChoice(value.configurationCode(), value.configurationName(), value.revisionId(),
                    value.revisionNo(), value.effectiveFrom(), value.effectiveTo());
        }
    }

    public record TaskCore(Long id, String taskNo, String taskName,
            String background, String taskOrigin, String cutoverType,
            String networkMode, Long configurationRevisionId, String configurationCode,
            Integer configurationRevisionNo, Long projectId, String projectName,
            Long ownerUserId, String currentStage, String taskStatus,
            String manualGrade, LocalDateTime scheduledTime, LocalDateTime createTime,
            Long version) {
        public static TaskCore from(CutoverTaskViews.TaskCore value) {
            if (value == null) return null;
            return new TaskCore(value.id(), value.taskNo(), value.taskName(),
                    value.background(), value.taskOrigin(), value.cutoverType(),
                    value.networkMode(), value.configurationRevisionId(), value.configurationCode(),
                    value.configurationRevisionNo(), value.projectId(), value.projectName(),
                    value.ownerUserId(), value.currentStage(), value.taskStatus(),
                    value.manualGrade(), value.scheduledTime(), value.createTime(),
                    value.version());
        }
    }

    public record Source(String intakeSourceType, String sourceSystem, String sourceBusinessNo,
            String businessEventId) {
        public static Source from(CutoverTaskViews.Source value) {
            if (value == null) return null;
            return new Source(value.intakeSourceType(), value.sourceSystem(), value.sourceBusinessNo(),
                    value.businessEventId());
        }
    }

    public record Project(Long projectId, String projectCode, String projectName,
            Long projectScopeVersion) {
        public static Project from(CutoverTaskViews.Project value) {
            if (value == null) return null;
            return new Project(value.projectId(), value.projectCode(), value.projectName(),
                    value.projectScopeVersion());
        }
    }

    public record Assessment(Long id, Long assessmentVersion, Long rowVersion,
            String status, String questionnaireTemplateCode, long questionnaireTemplateVersion,
            AssessmentAnswers answers, CustomerLevel customerServiceLevel, String manualGrade,
            boolean simpleFlow, Long submittedBy, LocalDateTime submittedAt,
            LocalDateTime invalidatedAt, String invalidationReason) {
        public static Assessment from(CutoverTaskViews.Assessment value) {
            if (value == null) return null;
            return new Assessment(value.id(), value.assessmentVersion(), value.rowVersion(),
                    value.status(), value.questionnaireTemplateCode(), value.questionnaireTemplateVersion(),
                    AssessmentAnswers.from(value.answers()), CustomerLevel.from(value.customerServiceLevel()), value.manualGrade(),
                    value.simpleFlow(), value.submittedBy(), value.submittedAt(),
                    value.invalidatedAt(), value.invalidationReason());
        }
    }

    public record WorkbenchStep(String stage, String label, String state,
            boolean isCurrent, boolean isAccessible) {
        public static WorkbenchStep from(CutoverTaskViews.WorkbenchStep value) {
            if (value == null) return null;
            return new WorkbenchStep(value.stage(), value.label(), value.state(),
                    value.isCurrent(), value.isAccessible());
        }
    }

    public record Detail(TaskCore task, Source source, Project project,
            List<Device> devices, CustomerLevel customerServiceLevel, Readiness implementationReadiness,
            Assessment assessment, List<WorkbenchStep> workbenchSteps, List<String> allowedActions) {
        public static Detail from(CutoverTaskViews.Detail value) {
            if (value == null) return null;
            return new Detail(TaskCore.from(value.task()), Source.from(value.source()), Project.from(value.project()),
                    value.devices() == null ? null : value.devices().stream().map(Device::from).toList(), CustomerLevel.from(value.customerServiceLevel()), Readiness.from(value.implementationReadiness()),
                    Assessment.from(value.assessment()), value.workbenchSteps() == null ? null : value.workbenchSteps().stream().map(WorkbenchStep::from).toList(), value.allowedActions());
        }
    }

    public record Device(Long deviceId, String serialNumber, Long projectId,
            long projectAssignmentVersion) {
        public static Device from(CutoverDeviceScopePort.DeviceFact value) {
            if (value == null) return null;
            return new Device(value.deviceId(), value.serialNumber(), value.projectId(),
                    value.projectAssignmentVersion());
        }
    }

    public record CustomerLevel(String status, Long customerId, String customerCode,
            String customerName, Long serviceLevelRevisionId, String serviceLevelCode,
            long factVersion, LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
        public static CustomerLevel from(CutoverCustomerLevelPort.CustomerLevelFact value) {
            if (value == null) return null;
            return new CustomerLevel(value.status(), value.customerId(), value.customerCode(),
                    value.customerName(), value.serviceLevelRevisionId(), value.serviceLevelCode(),
                    value.factVersion(), value.effectiveFrom(), value.effectiveTo());
        }
    }

    public record Readiness(Long snapshotId, long snapshotVersion, String decision,
            Long projectId, List<Long> deviceIds, Object sourceWatermark,
            List<String> unmetCodes) {
        public static Readiness from(CutoverReadinessPort.ReadinessFact value) {
            if (value == null) return null;
            return new Readiness(value.snapshotId(), value.snapshotVersion(), value.decision(),
                    value.projectId(), value.deviceIds(), value.sourceWatermark(),
                    value.unmetCodes());
        }
    }

    public record AssessmentAnswers(String businessImportanceLevel, String operationComplexityLevel, String hiddenRiskLevel,
            Boolean sparePartApplied) {
        public static AssessmentAnswers from(CutoverAssessmentAnswers value) {
            if (value == null) return null;
            return new AssessmentAnswers(value.businessImportanceLevel(), value.operationComplexityLevel(), value.hiddenRiskLevel(),
                    value.sparePartApplied());
        }
    }

    public record TaskCommand(Long taskId, String taskNo, String currentStage,
            String taskStatus, Long version, boolean replayed) {
        public static TaskCommand from(CutoverTaskCommandResult value) {
            if (value == null) return null;
            return new TaskCommand(value.taskId(), value.taskNo(), value.currentStage(),
                    value.taskStatus(), value.version(), value.replayed());
        }
    }

    public record AssessmentCommand(Long taskId, Long assessmentId, Long assessmentVersion,
            Long assessmentRowVersion, Long taskVersion, String status) {
        public static AssessmentCommand from(CutoverAssessmentCommandResult value) {
            if (value == null) return null;
            return new AssessmentCommand(value.taskId(), value.assessmentId(), value.assessmentVersion(),
                    value.assessmentRowVersion(), value.taskVersion(), value.status());
        }
    }

}
