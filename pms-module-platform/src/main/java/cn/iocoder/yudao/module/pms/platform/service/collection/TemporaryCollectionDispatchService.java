package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsGatewayApi;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchCommand;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchResult;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "pms.integration.device-ops", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TemporaryCollectionDispatchService implements cn.iocoder.yudao.module.pms.platform.api.collection.CollectionDispatchApi {

    private final CollectionTaskMapper taskMapper;
    private final DeviceOpsGatewayApi gatewayApi;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CollectionConnectionService savedConnections;

    @Override
    public void dispatchManual(cn.iocoder.yudao.module.pms.platform.api.collection.CollectionDispatchApi.Command command) {
        try {
            if (!java.util.Objects.equals(command.tenantId(),
                    cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId())) {
                throw new IllegalArgumentException("租户不匹配");
            }
            dispatch(new TemporaryDispatchCommand(command.tenantId(), command.platformTaskId(), command.commands(),
                    command.username(), command.secret(), "DEVICE_OPS", command.traceId()));
        } finally {
            if (command != null && command.secret() != null) Arrays.fill(command.secret(), '\0');
        }
    }

    @Override
    public void cancel(Long tenantId, String platformTaskId) {
        if (!java.util.Objects.equals(tenantId,
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("租户不匹配");
        }
        var task = taskMapper.selectByTenantAndPlatformTaskId(tenantId, platformTaskId);
        if (task == null) throw new IllegalStateException("采集任务不存在");
        if (java.util.Set.of("COMPLETED", "RESULT_AVAILABLE", "FAILED", "CANCELLED", "SECURITY_EXCEPTION").contains(task.getStatus())) return;
        gatewayApi.cancel(platformTaskId, "USER_REQUESTED");
        // An accepted cancel alone is not terminal proof. Query the persisted provider fence when dispatch is uncertain.
        task = taskMapper.selectByTenantAndPlatformTaskId(tenantId, platformTaskId);
        if (CollectionTaskStateMachine.canCancelBeforeDispatch(task.getStatus(), task.getTechnicalStage(), task.getExternalTaskId())) {
            CollectionTaskReconciliationService.applyUndispatchedCancellation(taskMapper, task, gatewayApi.query(platformTaskId));
        }
    }

    public DeviceOpsDispatchResult dispatch(TemporaryDispatchCommand command) {
        return dispatchResolved(command, null, null);
    }

    @Override public void dispatchSaved(cn.iocoder.yudao.module.pms.platform.api.collection.CollectionDispatchApi.SavedCommand command) {
        if (!java.util.Objects.equals(command.tenantId(), cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId())
                || blank(command.connectionId())) throw new IllegalArgumentException("已保存连接下发参数不完整");
        dispatchResolved(new TemporaryDispatchCommand(command.tenantId(), command.platformTaskId(), command.commands(),
                command.username(), null, "DEVICE_OPS", command.traceId()), command.connectionId(), command.connectionVersion());
    }

    private DeviceOpsDispatchResult dispatchResolved(TemporaryDispatchCommand command, String connectionId, Long connectionVersion) {
        char[] secret = command == null ? null : command.temporarySecret();
        try {
            if (connectionId == null) validate(command);
            CollectionTaskDO task = taskMapper.selectByTenantAndPlatformTaskId(
                    command.tenantId(), command.platformTaskId());
            if (connectionId == null) requirePendingTemporaryTask(task);
            else if (task == null || !"SAVED_CREDENTIAL".equals(task.getCredentialMode()) || !"PENDING_DISPATCH".equals(task.getTechnicalStage())) {
                throw new IllegalStateException("COLLECTION_TASK_NOT_PENDING_SAVED_DISPATCH");
            }
            if (connectionId != null && (savedConnections == null || !savedConnections.dispatchAuthorized(task, connectionId, connectionVersion))) {
                update(task, CollectionTaskStateMachine.rejectedBeforeDispatch(task.getStatus(), task.getTechnicalStage()),
                        "DISPATCH_FAILED", null, "AUTHORIZATION_REJECTED", "CREDENTIAL_AUTHORIZATION_REVOKED");
                throw new IllegalStateException("连接授权已失效，命令未下发");
            }
            // Commit the claim before external I/O so a process crash remains visible to reconciliation.
            update(task, task.getStatus(), "DISPATCHING", null, null, null);
            task.setTechnicalStage("DISPATCHING");
            DeviceOpsDispatchCommand gatewayCommand = new DeviceOpsDispatchCommand(
                    task.getPlatformTaskId(), String.valueOf(task.getBatchId()), task.getTenantId(),
                    task.getProjectId(), task.getDeviceId(), task.getDeviceName(), task.getHost(), task.getPort(),
                    task.getProtocol(), task.getTemplateId(), task.getTemplateVersion(), task.getTemplateHash(),
                    List.copyOf(command.commands()), task.getCredentialMode(), null, command.temporaryUsername(), secret,
                    command.callbackProvider(), command.traceId(), task.getSourceContext(), task.getSourceObjectType(), connectionId, connectionVersion);
            DeviceOpsDispatchResult result = null;
            try {
                result = gatewayApi.dispatch(gatewayCommand);
                if (result.accepted()) {
                    update(task, CollectionTaskStateMachine.acceptedDispatchStatus(task.getStatus()),
                            "ACCEPTED", result.externalTaskId(), result.externalStatus(), null);
                    return result;
                }
                update(task, "FAILED", "DISPATCH_FAILED", result.externalTaskId(), result.externalStatus(),
                        "EXPLICIT_REJECTION");
                throw new IllegalStateException("DEVICE_OPS_DISPATCH_REJECTED");
            } catch (RuntimeException ex) {
                if ("DEVICE_OPS_DISPATCH_REJECTED".equals(ex.getMessage())) {
                    throw ex;
                }
                if (result != null && result.accepted()) {
                    update(task, task.getStatus(), "RECONCILING", result.externalTaskId(), result.externalStatus(), "LOCAL_ACK_FAILED");
                    throw new IllegalStateException("DEVICE_OPS_DISPATCH_UNKNOWN");
                }
                if (hasIoCause(ex)) {
                    update(task, task.getStatus(), "RECONCILING", null, "UNKNOWN", "NETWORK_UNKNOWN");
                    throw new IllegalStateException("DEVICE_OPS_DISPATCH_UNKNOWN", ex);
                }
                update(task, "FAILED", "DISPATCH_FAILED", null, "CLIENT_ERROR", "CLIENT_DISPATCH_ERROR");
                throw new IllegalStateException("DEVICE_OPS_DISPATCH_FAILED", ex);
            }
        } finally {
            if (secret != null) Arrays.fill(secret, '\0');
        }
    }

    private void update(CollectionTaskDO task, String status, String technicalStage, String externalTaskId,
                        String externalStatus, String failureCategory) {
        int updated = taskMapper.updateDispatchState(new CollectionTaskDispatchUpdate(
                task.getTenantId(), task.getPlatformTaskId(), task.getTechnicalStage(), status, technicalStage,
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
