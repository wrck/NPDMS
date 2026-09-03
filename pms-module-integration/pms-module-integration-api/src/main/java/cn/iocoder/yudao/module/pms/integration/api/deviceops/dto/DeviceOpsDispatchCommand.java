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
        String traceId) {
}
