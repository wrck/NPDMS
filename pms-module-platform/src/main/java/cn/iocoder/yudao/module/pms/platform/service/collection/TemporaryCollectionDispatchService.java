package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsGatewayApi;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchCommand;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchResult;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@ConditionalOnBean(DeviceOpsGatewayApi.class)
@RequiredArgsConstructor
public class TemporaryCollectionDispatchService {

    private final CollectionTaskMapper taskMapper;
    private final DeviceOpsGatewayApi gatewayApi;

    public DeviceOpsDispatchResult dispatch(TemporaryDispatchCommand command) {
        validate(command);
        char[] secret = command.temporarySecret();
        try {
            CollectionTaskDO task = taskMapper.selectByTenantAndPlatformTaskId(
                    command.tenantId(), command.platformTaskId());
            requirePendingTemporaryTask(task);
            DeviceOpsDispatchCommand gatewayCommand = new DeviceOpsDispatchCommand(
                    task.getPlatformTaskId(), String.valueOf(task.getBatchId()), task.getTenantId(),
                    task.getProjectId(), task.getDeviceId(), task.getDeviceName(), task.getHost(), task.getPort(),
                    task.getProtocol(), task.getTemplateId(), task.getTemplateVersion(), task.getTemplateHash(),
                    List.copyOf(command.commands()), "TEMPORARY_SECRET", null, command.temporaryUsername(), secret,
                    command.callbackProvider(), command.traceId());
            try {
                DeviceOpsDispatchResult result = gatewayApi.dispatch(gatewayCommand);
                if (result.accepted()) {
                    update(task, "DISPATCHED", "ACCEPTED", result.externalTaskId(), result.externalStatus(), null);
                    return result;
                }
                update(task, "FAILED", "DISPATCH_FAILED", result.externalTaskId(), result.externalStatus(),
                        "EXPLICIT_REJECTION");
                throw new IllegalStateException("DEVICE_OPS_DISPATCH_REJECTED");
            } catch (RuntimeException ex) {
                if ("DEVICE_OPS_DISPATCH_REJECTED".equals(ex.getMessage())) {
                    throw ex;
                }
                if (hasIoCause(ex)) {
                    update(task, task.getStatus(), "RECONCILING", null, "UNKNOWN", "NETWORK_UNKNOWN");
                    throw new IllegalStateException("DEVICE_OPS_DISPATCH_UNKNOWN", ex);
                }
                update(task, "FAILED", "DISPATCH_FAILED", null, "CLIENT_ERROR", "CLIENT_DISPATCH_ERROR");
                throw new IllegalStateException("DEVICE_OPS_DISPATCH_FAILED", ex);
            }
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private void update(CollectionTaskDO task, String status, String technicalStage, String externalTaskId,
                        String externalStatus, String failureCategory) {
        int updated = taskMapper.updateDispatchState(new CollectionTaskDispatchUpdate(
                task.getTenantId(), task.getPlatformTaskId(), "PENDING_DISPATCH", status, technicalStage,
                externalTaskId, externalStatus, failureCategory));
        if (updated != 1) {
            throw new IllegalStateException("COLLECTION_TASK_DISPATCH_STATE_CONFLICT");
        }
    }

    private static void requirePendingTemporaryTask(CollectionTaskDO task) {
        if (task == null) {
            throw new IllegalStateException("COLLECTION_TASK_NOT_FOUND");
        }
        if (!"TEMPORARY_SECRET".equals(task.getCredentialMode())
                || !"PENDING_DISPATCH".equals(task.getTechnicalStage())) {
            throw new IllegalStateException("COLLECTION_TASK_NOT_PENDING_TEMPORARY_DISPATCH");
        }
    }

    private static void validate(TemporaryDispatchCommand command) {
        if (command == null || command.tenantId() == null || blank(command.platformTaskId())
                || command.commands() == null || command.commands().isEmpty() || blank(command.temporaryUsername())
                || command.temporarySecret() == null || command.temporarySecret().length == 0
                || blank(command.callbackProvider()) || blank(command.traceId())) {
            throw new IllegalArgumentException("临时凭证下发参数不完整");
        }
        if (command.commands().stream().anyMatch(TemporaryCollectionDispatchService::blank)) {
            throw new IllegalArgumentException("采集命令不能为空");
        }
    }

    private static boolean hasIoCause(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof java.io.IOException) {
                return true;
            }
        }
        return false;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record TemporaryDispatchCommand(
            Long tenantId,
            String platformTaskId,
            List<String> commands,
            String temporaryUsername,
            char[] temporarySecret,
            String callbackProvider,
            String traceId) {
    }
}
