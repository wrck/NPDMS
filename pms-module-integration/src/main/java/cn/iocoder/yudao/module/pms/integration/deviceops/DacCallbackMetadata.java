package cn.iocoder.yudao.module.pms.integration.deviceops;

public record DacCallbackMetadata(Long tenantId, String callbackId, String platformTaskId,
        String externalTaskId, String externalStatus, long sequence, long resultVersion,
        long sizeBytes, String sha256, String failureCategory, String traceId) { }
