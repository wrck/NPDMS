package cn.iocoder.yudao.module.pms.integration.api.deviceops.dto;

import java.util.List;

public record DeviceOpsDispatchCommand(
        String platformTaskId,
        String batchId,
        Long tenantId,
        String projectId,
        String deviceId,
        String deviceName,
        String host,
        Integer port,
        String protocol,
        String templateId,
        String templateVersion,
        String templateHash,
        List<String> commands,
        String credentialMode,
        String credentialToken,
        String temporaryUsername,
        char[] temporarySecret,
        String callbackProvider,
        String traceId,
        String sourceContext,
        String sourceObjectType,
        String savedConnectionId,
        Long savedConnectionVersion) {
    public DeviceOpsDispatchCommand(String platformTaskId, String batchId, Long tenantId, String projectId,
            String deviceId, String deviceName, String host, Integer port, String protocol, String templateId,
            String templateVersion, String templateHash, List<String> commands, String credentialMode,
            String credentialToken, String temporaryUsername, char[] temporarySecret, String callbackProvider,
            String traceId, String sourceContext, String sourceObjectType) {
        this(platformTaskId, batchId, tenantId, projectId, deviceId, deviceName, host, port, protocol, templateId,
                templateVersion, templateHash, commands, credentialMode, credentialToken, temporaryUsername,
                temporarySecret, callbackProvider, traceId, sourceContext, sourceObjectType, null, null);
    }
}
