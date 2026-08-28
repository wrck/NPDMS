package cn.iocoder.yudao.module.pms.asset.service.assignment;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceCustomerRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceCustomerAssignmentUpdate;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.AssignDeviceCustomerCommand;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.DeviceCustomerAssignmentResult;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceCustomerAssignmentService {

    public static final String ASSIGN_SCOPE = "POST:/api/v1/pms/devices/{id}/actions/assign-customer";

    private final CustomerQueryApi customerQueryApi;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final DeviceAssignmentMapper assignmentMapper;

    public DeviceCustomerAssignmentResult assign(AssignDeviceCustomerCommand command) {
        validate(command);
        CustomerSummaryDTO customer = customerQueryApi.getCustomer(command.customerId());
        if (customer == null || !Objects.equals(customer.tenantId(), command.tenantId())) {
            throw new IllegalStateException("CUSTOMER_NOT_FOUND");
        }
        if (!"ENABLED".equals(customer.lifecycleStatus())) {
            throw new IllegalStateException("CUSTOMER_NOT_REFERENCEABLE");
        }
        PlatformCommandExecutionApi.ExecutionResult<DeviceCustomerAssignmentResult> execution =
                commandExecutionApi.execute(
                        new PlatformCommandExecutionApi.IdempotencyScope(
                                command.tenantId(), ASSIGN_SCOPE, command.actorId(), command.idempotencyKey()),
                        command.requestDigest(), DeviceCustomerAssignmentResult.class,
                        () -> assignOnce(command),
                        result -> successFacts(command, result));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw new IllegalStateException("IDEMPOTENCY_CONFLICT");
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw new IllegalStateException("IDEMPOTENCY_IN_PROGRESS");
        }
        DeviceCustomerAssignmentResult result = execution.response();
        if (execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED) {
            return new DeviceCustomerAssignmentResult(result.deviceId(), result.customerId(),
                    result.assignmentVersion(), result.operationId(), true);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    protected DeviceCustomerAssignmentResult assignOnce(AssignDeviceCustomerCommand command) {
        DeviceAssignmentLockQuery lockQuery = new DeviceAssignmentLockQuery(
                command.tenantId(), command.deviceId());
        DeviceDO device = assignmentMapper.selectDeviceForUpdate(lockQuery);
        if (device == null || !Objects.equals(device.getTenantId(), command.tenantId())) {
            throw new IllegalStateException("DEVICE_NOT_FOUND");
        }
        if (!Objects.equals(device.getCustomerAssignmentVersion(), command.expectedAssignmentVersion())) {
            throw new IllegalStateException("VERSION_CONFLICT");
        }
        long newVersion = command.expectedAssignmentVersion() + 1;
        String operationId = UUID.randomUUID().toString();
        DeviceCustomerRelationshipDO current = assignmentMapper.selectCurrentCustomer(lockQuery);
        if (current != null && assignmentMapper.closeCurrentCustomer(
                command.tenantId(), command.deviceId(), command.effectiveAt(), newVersion) != 1) {
            throw new IllegalStateException("VERSION_CONFLICT");
        }
        assignmentMapper.insertCustomerRelationship(newRelationship(
                command, device, operationId, newVersion));
        if (assignmentMapper.updateDeviceCustomerIfMatch(new DeviceCustomerAssignmentUpdate(
                command.tenantId(), command.deviceId(), command.customerId(),
                command.expectedAssignmentVersion(), newVersion)) != 1) {
            throw new IllegalStateException("VERSION_CONFLICT");
        }
        return new DeviceCustomerAssignmentResult(
                command.deviceId(), command.customerId(), newVersion, operationId, false);
    }

    private DeviceCustomerRelationshipDO newRelationship(
            AssignDeviceCustomerCommand command,
            DeviceDO device,
            String operationId,
            long newVersion) {
        DeviceCustomerRelationshipDO relationship = new DeviceCustomerRelationshipDO();
        relationship.setTenantId(command.tenantId());
        relationship.setDeviceSn(device.getSn());
        relationship.setCustomerId(command.customerId());
        relationship.setRelationshipType(command.relationshipType());
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

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            AssignDeviceCustomerCommand command,
            DeviceCustomerAssignmentResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceId", result.deviceId());
        payload.put("newCustomerId", result.customerId());
        payload.put("assignmentVersion", result.assignmentVersion());
        payload.put("effectiveAt", command.effectiveAt());
        payload.put("operationId", result.operationId());
        return new PlatformCommandExecutionApi.SuccessFacts(
                "DEVICE_ASSIGN_CUSTOMER", "Device", String.valueOf(result.deviceId()),
                command.correlationId(), JsonUtils.toJsonString(payload),
                null, null);
    }

    private void validate(AssignDeviceCustomerCommand command) {
        if (command == null || command.tenantId() == null || command.deviceId() == null
                || command.customerId() == null || !"DIRECT".equals(command.relationshipType())
                || command.expectedAssignmentVersion() == null || command.expectedAssignmentVersion() < 0
                || command.reason() == null || command.reason().isBlank()
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.requestDigest() == null || !command.requestDigest().matches("[0-9a-f]{64}")
                || command.actorId() == null || command.correlationId() == null
                || command.correlationId().isBlank() || command.effectiveAt() == null) {
            throw new IllegalArgumentException("设备客户归属命令不完整");
        }
    }
}
