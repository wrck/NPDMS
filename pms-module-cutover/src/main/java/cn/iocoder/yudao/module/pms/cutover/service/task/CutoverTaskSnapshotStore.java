package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverAssessmentDeviceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverAssessmentDeviceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverFrozenDeviceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskDeviceListQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverAssessmentRowQuery;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.*;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** P1/P2 的固定上下文字段与冻结设备。旧 JSON 不是读取来源。 */
@Service
@RequiredArgsConstructor
public class CutoverTaskSnapshotStore {
    private final CutoverFrozenDeviceMapper taskDevices;
    private final CutoverAssessmentDeviceMapper assessmentDevices;
    private final CutoverAssessmentMapper assessments;
    private final CutoverReadinessPort readiness;

    @Builder
    public record Context(CutoverProjectContextPort.ProjectContextFact project,
                          CutoverCustomerLevelPort.CustomerLevelFact customer,
                          CutoverReadinessPort.ReadinessFact readiness, String intakeSourceType) { }
    public record TaskWrite(CutoverTaskDO task, Context context) { }
    public record AssessmentWrite(CutoverAssessmentDO assessment, Context context, CutoverAssessmentAnswers answers) { }
    @Builder
    public record DeviceWrite(CutoverAssessmentDO assessment, List<CutoverTaskDeviceScopeDO> devices,
                              Map<Long, CutoverDeviceProductTypePort.ProductTypeFact> productTypes,
                              Long actorId, LocalDateTime time,
                              List<CutoverDeviceScopePort.DeviceFact> currentDevices) { }

    public static void applyTask(TaskWrite command) {
        var row = command.task(); var context = command.context(); var project = context.project(); var customer = context.customer();
        row.setProjectId(project.projectId()); row.setProjectVersion((long) project.projectVersion());
        row.setProjectCode(project.projectCode()); row.setProjectName(project.projectName());
        row.setCustomerId(project.customerId()); row.setCustomerCode(project.customerCode()); row.setCustomerName(project.customerName());
        row.setDepartmentId(project.departmentId()); row.setDepartmentCode(project.departmentCode()); row.setDepartmentName(project.departmentName());
        row.setProjectScopeVersion(project.projectScopeVersion());
        row.setCustomerFactStatusCode(customer.status()); row.setServiceLevelRevisionId(customer.serviceLevelRevisionId());
        row.setServiceLevelCode(customer.serviceLevelCode()); row.setCustomerFactVersion(customer.factVersion());
        row.setServiceLevelSourceRecordId(customer.sourceRecordId());
        row.setServiceLevelEffectiveStartTime(customer.effectiveFrom()); row.setServiceLevelEffectiveEndTime(customer.effectiveTo());
        row.setReadinessDecisionCode(context.readiness().decision());
        row.setImplementationReadinessSnapshotId(context.readiness().snapshotId());
        row.setImplementationReadinessSnapshotVersion(context.readiness().snapshotId() == null ? null : context.readiness().snapshotVersion());
    }

    public static void applyAssessment(AssessmentWrite command) {
        var row = command.assessment(); var context = command.context(); var project = context.project(); var customer = context.customer();
        if (command.answers() != null) {
            row.setBusinessImportanceLevel(command.answers().businessImportanceLevel());
            row.setOperationComplexityLevel(command.answers().operationComplexityLevel());
            row.setHiddenRiskLevel(command.answers().hiddenRiskLevel()); row.setSparePartAppliedFlag(command.answers().sparePartApplied());
        }
        row.setIntakeSourceTypeCode(context.intakeSourceType());
        row.setProjectId(project.projectId()); row.setProjectVersion((long) project.projectVersion());
        row.setProjectCode(project.projectCode()); row.setProjectName(project.projectName());
        row.setCustomerId(project.customerId()); row.setCustomerCode(project.customerCode()); row.setCustomerName(project.customerName());
        row.setDepartmentId(project.departmentId()); row.setDepartmentCode(project.departmentCode()); row.setDepartmentName(project.departmentName());
        row.setProjectScopeVersion(project.projectScopeVersion());
        row.setCustomerFactStatusCode(customer.status()); row.setServiceLevelRevisionId(customer.serviceLevelRevisionId());
        row.setServiceLevelCode(customer.serviceLevelCode()); row.setCustomerFactVersion(customer.factVersion());
        row.setServiceLevelSourceRecordId(customer.sourceRecordId());
        row.setServiceLevelEffectiveStartTime(customer.effectiveFrom()); row.setServiceLevelEffectiveEndTime(customer.effectiveTo());
        row.setReadinessDecisionCode(context.readiness().decision());
        row.setImplementationReadinessSnapshotId(context.readiness().snapshotId());
        row.setImplementationReadinessSnapshotVersion(context.readiness().snapshotId() == null ? null : context.readiness().snapshotVersion());
    }

    public static CutoverProjectContextPort.ProjectContextFact project(CutoverTaskDO row) {
        if (row.getProjectVersion() == null || row.getProjectScopeVersion() == null) return null;
        return CutoverProjectContextPort.ProjectContextFact.builder().tenantId(row.getTenantId()).projectId(row.getProjectId())
                .projectVersion(Math.toIntExact(row.getProjectVersion())).projectCode(row.getProjectCode()).projectName(row.getProjectName())
                .customerId(row.getCustomerId()).customerCode(row.getCustomerCode()).customerName(row.getCustomerName())
                .departmentId(row.getDepartmentId()).departmentCode(row.getDepartmentCode()).departmentName(row.getDepartmentName())
                .projectScopeVersion(row.getProjectScopeVersion()).build();
    }

    public static CutoverProjectContextPort.ProjectContextFact project(CutoverAssessmentDO row) {
        if (row.getProjectVersion() == null || row.getProjectScopeVersion() == null) return null;
        return CutoverProjectContextPort.ProjectContextFact.builder().tenantId(row.getTenantId()).projectId(row.getProjectId())
                .projectVersion(Math.toIntExact(row.getProjectVersion())).projectCode(row.getProjectCode()).projectName(row.getProjectName())
                .customerId(row.getCustomerId()).customerCode(row.getCustomerCode()).customerName(row.getCustomerName())
                .departmentId(row.getDepartmentId()).departmentCode(row.getDepartmentCode()).departmentName(row.getDepartmentName())
                .projectScopeVersion(row.getProjectScopeVersion()).build();
    }

    public static CutoverCustomerLevelPort.CustomerLevelFact customer(CutoverTaskDO row) {
        if (row.getCustomerFactVersion() == null) return null;
        return CutoverCustomerLevelPort.CustomerLevelFact.builder()
                .status(row.getCustomerFactStatusCode())
                .customerId(row.getCustomerId())
                .customerCode(row.getCustomerCode())
                .customerName(row.getCustomerName())
                .serviceLevelRevisionId(row.getServiceLevelRevisionId())
                .sourceRecordId(row.getServiceLevelSourceRecordId())
                .serviceLevelCode(row.getServiceLevelCode())
                .factVersion(row.getCustomerFactVersion())
                .effectiveFrom(row.getServiceLevelEffectiveStartTime())
                .effectiveTo(row.getServiceLevelEffectiveEndTime())
                .build();
    }

    public static CutoverCustomerLevelPort.CustomerLevelFact customer(CutoverAssessmentDO row) {
        if (row.getCustomerFactVersion() == null) return null;
        return CutoverCustomerLevelPort.CustomerLevelFact.builder()
                .status(row.getCustomerFactStatusCode())
                .customerId(row.getCustomerId())
                .customerCode(row.getCustomerCode())
                .customerName(row.getCustomerName())
                .serviceLevelRevisionId(row.getServiceLevelRevisionId())
                .sourceRecordId(row.getServiceLevelSourceRecordId())
                .serviceLevelCode(row.getServiceLevelCode())
                .factVersion(row.getCustomerFactVersion())
                .effectiveFrom(row.getServiceLevelEffectiveStartTime())
                .effectiveTo(row.getServiceLevelEffectiveEndTime())
                .build();
    }

    public static String effectiveCustomerServiceLevel(CutoverAssessmentDO row) {
        return row.getManualCustomerServiceLevelCode() != null ? row.getManualCustomerServiceLevelCode() : row.getServiceLevelCode();
    }

    public static boolean hasRequiredCustomerServiceLevelSelection(CutoverAssessmentDO row) {
        // 保留独立割接原有的外部参考不可用边界；只校验新增的未维护手选路径。
        return !"NOT_CONFIGURED".equals(row.getCustomerFactStatusCode())
                || row.getManualCustomerServiceLevelCode() != null
                && row.getManualCustomerServiceLevelSelectedBy() != null && row.getManualCustomerServiceLevelSelectedAt() != null;
    }

    public static CutoverAssessmentAnswers answers(CutoverAssessmentDO row) {
        return new CutoverAssessmentAnswers(row.getBusinessImportanceLevel(), row.getOperationComplexityLevel(),
                row.getHiddenRiskLevel(), row.getSparePartAppliedFlag());
    }

    public CutoverReadinessPort.ReadinessFact readiness(CutoverTaskDO task) {
        var ids = taskDevices.byTask(new CutoverTaskDeviceListQuery(task.getTenantId(), task.getId())).stream()
                .map(CutoverTaskDeviceScopeDO::getDeviceId).toList();
        if ("NOT_APPLICABLE".equals(task.getReadinessDecisionCode())) {
            return CutoverContextSupport.independentReadiness(task.getProjectId(), ids);
        }
        if (task.getImplementationReadinessSnapshotId() == null) return null;
        return readiness.readSnapshot(CutoverReadinessPort.SnapshotReference.builder().tenantId(task.getTenantId()).projectId(task.getProjectId())
                .snapshotId(task.getImplementationReadinessSnapshotId()).snapshotVersion(task.getImplementationReadinessSnapshotVersion())
                .deviceIds(ids).build());
    }

    /** 只读审批查看CUT已经保存的来源引用，不据此复核或推进IMP业务。 */
    public CutoverReadinessPort.ReadinessFact readinessReference(CutoverTaskDO task) {
        var ids = taskDevices.byTask(new CutoverTaskDeviceListQuery(task.getTenantId(), task.getId())).stream()
                .map(CutoverTaskDeviceScopeDO::getDeviceId).toList();
        if ("NOT_APPLICABLE".equals(task.getReadinessDecisionCode())) return CutoverContextSupport.independentReadiness(task.getProjectId(), ids);
        if (task.getImplementationReadinessSnapshotId() == null) return null;
        return new CutoverReadinessPort.ReadinessFact(task.getImplementationReadinessSnapshotId(), task.getImplementationReadinessSnapshotVersion(),
                task.getReadinessDecisionCode(), task.getProjectId(), ids, null, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceAssessmentDevices(DeviceWrite command) {
        var assessment = command.assessment();
        if (!"DRAFT".equals(assessment.getAssessmentStatus())) throw new IllegalArgumentException("已提交评估设备事实不可覆盖");
        assessments.deleteDraftDeviceFacts(new CutoverAssessmentRowQuery(assessment.getTenantId(), assessment.getCutoverTaskId(), assessment.getId()));
        for (int i = 0; i < command.devices().size(); i++) {
            var device = command.devices().get(i);
            if (!assessment.getTenantId().equals(device.getTenantId()) || !assessment.getCutoverTaskId().equals(device.getCutoverTaskId())) {
                throw new IllegalArgumentException("评估设备必须属于同租户任务");
            }
            var product = command.productTypes().get(device.getDeviceId());
            var current = command.currentDevices() == null ? null : command.currentDevices().stream()
                    .filter(value -> device.getDeviceId().equals(value.deviceId())).findFirst().orElseThrow();
            assessmentDevices.insert(CutoverAssessmentDeviceDO.builder().tenantId(assessment.getTenantId()).assessmentId(assessment.getId())
                    .taskDeviceScopeId(device.getId()).deviceId(device.getDeviceId()).projectId(current == null ? device.getProjectId() : current.projectId()).sn(current == null ? device.getSerialNumberSnapshot() : current.serialNumber())
                    .projectAssignmentVersion(current == null ? device.getProjectAssignmentVersion() : current.projectAssignmentVersion()).deviceTypeCode(device.getDeviceTypeCodeSnapshot())
                    .deviceTypeSourceVersion(device.getDeviceTypeSourceVersionSnapshot()).productTypeCode(product == null ? null : product.productTypeCode())
                    .productTypeSourceVersion(product == null ? null : product.sourceVersion()).sortOrder(i).createUserId(command.actorId()).createTime(command.time()).build());
        }
    }
}
