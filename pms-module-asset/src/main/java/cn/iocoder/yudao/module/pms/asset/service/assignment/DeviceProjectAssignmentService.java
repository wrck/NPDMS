package cn.iocoder.yudao.module.pms.asset.service.assignment;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceAssignmentReconciliationDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceProjectAssignmentUpdate;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.AssignDeviceProjectCommand;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.DeviceProjectAssignmentResult;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceProjectAssignmentService {

    public static final String ASSIGN_SCOPE = "POST:/api/v1/pms/devices/{id}/actions/assign-project";

    private final ProjectDeviceAssignmentGuardApi projectGuardApi;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final DeviceAssignmentMapper assignmentMapper;

    public DeviceProjectAssignmentResult assign(AssignDeviceProjectCommand command) {
        validate(command);
        ProjectDeviceAssignmentGuardResult guard = projectGuardApi.validate(
                new ProjectDeviceAssignmentGuardQuery(
                        command.tenantId(), command.projectId(), command.actorId()));
        if (guard == null || !guard.assignable()) {
            throw new IllegalStateException(guard == null ? "PROJECT_NOT_FOUND" : guard.rejectionCode());
        }
        PlatformCommandExecutionApi.ExecutionResult<DeviceProjectAssignmentResult> execution =
                commandExecutionApi.execute(
                        new PlatformCommandExecutionApi.IdempotencyScope(
                                command.tenantId(), ASSIGN_SCOPE, command.actorId(), command.idempotencyKey()),
                        command.requestDigest(), DeviceProjectAssignmentResult.class,
                        () -> assignOnce(command, guard),
                        result -> successFacts(command, result));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw new IllegalStateException("IDEMPOTENCY_CONFLICT");
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw new IllegalStateException("IDEMPOTENCY_IN_PROGRESS");
        }
        DeviceProjectAssignmentResult result = execution.response();
        if (execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED) {
            return new DeviceProjectAssignmentResult(result.deviceId(), result.oldProjectId(), result.projectId(),
                    result.assignmentVersion(), result.operationId(), true);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    protected DeviceProjectAssignmentResult assignOnce(
            AssignDeviceProjectCommand command,
            ProjectDeviceAssignmentGuardResult guard) {
        DeviceAssignmentLockQuery lockQuery = new DeviceAssignmentLockQuery(
                command.tenantId(), command.deviceId());
        DeviceDO device = assignmentMapper.selectDeviceForUpdate(lockQuery);
        if (device == null || !Objects.equals(device.getTenantId(), command.tenantId())) {
            throw new IllegalStateException("DEVICE_NOT_FOUND");
        }
        if (!Objects.equals(device.getProjectAssignmentVersion(), command.expectedAssignmentVersion())) {
            throw new IllegalStateException("VERSION_CONFLICT");
        }
        long newVersion = command.expectedAssignmentVersion() + 1;
        LocalDateTime effectiveAt = command.effectiveAt();
        String operationId = UUID.randomUUID().toString();
        DeviceProjectRelationshipDO current = assignmentMapper.selectCurrentProject(lockQuery);
        if (current != null && assignmentMapper.closeCurrentProject(
                command.tenantId(), command.deviceId(), effectiveAt, newVersion) != 1) {
            throw new IllegalStateException("VERSION_CONFLICT");
        }
        assignmentMapper.insertProjectRelationship(newRelationship(
                command, device, operationId, newVersion));
        if (assignmentMapper.updateDeviceProjectIfMatch(new DeviceProjectAssignmentUpdate(
                command.tenantId(), command.deviceId(), command.projectId(),
                command.expectedAssignmentVersion(), newVersion)) != 1) {
            throw new IllegalStateException("VERSION_CONFLICT");
        }
        if (guard.customerId() != null && device.getCustomerId() != null
                && !Objects.equals(guard.customerId(), device.getCustomerId())) {
            assignmentMapper.insertReconciliation(reconciliation(command, device, guard));
        }
        return new DeviceProjectAssignmentResult(
                command.deviceId(), device.getProjectId(), command.projectId(), newVersion, operationId, false);
    }

    private DeviceProjectRelationshipDO newRelationship(
            AssignDeviceProjectCommand command,
            DeviceDO device,
            String operationId,
            long newVersion) {
        DeviceProjectRelationshipDO relationship = new DeviceProjectRelationshipDO();
        relationship.setTenantId(command.tenantId());
        relationship.setDeviceSn(device.getSn());
        relationship.setProjectId(command.projectId());
        relationship.setRelationshipType("DIRECT");
        relationship.setEffectiveFrom(command.effectiveAt());
        relationship.setAssignmentVersion(newVersion);
        relationship.setReason(command.reason());
        relationship.setOperationId(operationId);
        relationship.setSourceSystem("PMS");
        relationship.setSourceKey(operationId);
        relationship.setSourceVersion(String.valueOf(newVersion));
        relationship.setVersion(0);
        return relationship;
    }

    private DeviceAssignmentReconciliationDO reconciliation(
            AssignDeviceProjectCommand command,
            DeviceDO device,
            ProjectDeviceAssignmentGuardResult guard) {
        DeviceAssignmentReconciliationDO reconciliation = new DeviceAssignmentReconciliationDO();
        reconciliation.setTenantId(command.tenantId());
        reconciliation.setDeviceSn(device.getSn());
        reconciliation.setProjectId(command.projectId());
        reconciliation.setProjectCustomerId(guard.customerId());
        reconciliation.setDeviceCustomerId(device.getCustomerId());
        reconciliation.setStatus("PENDING");
        reconciliation.setReason("PROJECT_CUSTOMER_MISMATCH");
        reconciliation.setVersion(0);
        return reconciliation;
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            AssignDeviceProjectCommand command,
            DeviceProjectAssignmentResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceId", result.deviceId());
        payload.put("oldProjectId", result.oldProjectId());
        payload.put("newProjectId", result.projectId());
        payload.put("assignmentVersion", result.assignmentVersion());
        payload.put("effectiveAt", command.effectiveAt());
        payload.put("operationId", result.operationId());
        return new PlatformCommandExecutionApi.SuccessFacts(
                "DEVICE_ASSIGN_PROJECT", "Device", String.valueOf(result.deviceId()),
                command.correlationId(), JsonUtils.toJsonString(payload),
                "DeviceAssigned", JsonUtils.toJsonString(payload));
    }

    private void validate(AssignDeviceProjectCommand command) {
        if (command == null || command.tenantId() == null || command.deviceId() == null
                || command.projectId() == null || command.expectedAssignmentVersion() == null
                || command.expectedAssignmentVersion() < 0 || command.reason() == null
                || command.reason().isBlank() || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank() || command.requestDigest() == null
                || !command.requestDigest().matches("[0-9a-f]{64}") || command.actorId() == null
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.effectiveAt() == null) {
            throw new IllegalArgumentException("设备项目归属命令不完整");
        }
    }
}
