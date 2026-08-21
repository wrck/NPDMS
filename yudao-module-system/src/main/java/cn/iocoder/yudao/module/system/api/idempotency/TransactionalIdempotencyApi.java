package cn.iocoder.yudao.module.system.api.idempotency;

import cn.iocoder.yudao.module.system.api.idempotency.dto.IdempotencyDecision;

/** PM-01 与正式业务事实同事务提交的幂等边界。 */
public interface TransactionalIdempotencyApi {

    IdempotencyDecision begin(long tenantId, long actorId, String scopeCode,
                              String idempotencyKey, String requestSha256);

    void complete(long recordId, long resourceId, String responseJson);
}
