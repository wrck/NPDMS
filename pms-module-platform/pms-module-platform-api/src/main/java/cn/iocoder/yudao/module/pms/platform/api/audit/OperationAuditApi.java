package cn.iocoder.yudao.module.pms.platform.api.audit;

import java.util.Map;

/** 平台业务操作审计公开契约。 */
public interface OperationAuditApi {

    void record(Long tenantId, Long actorId, String correlationId, String operationCode,
                Long requestId, String resultCode, Map<String, ?> safeDetail);

    void record(Long tenantId, Long actorId, String correlationId, String operationCode,
                String aggregateType, String aggregateKey, String resultCode,
                Map<String, ?> safeDetail);
}
