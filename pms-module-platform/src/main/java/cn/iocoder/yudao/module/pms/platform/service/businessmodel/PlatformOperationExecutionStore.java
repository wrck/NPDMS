package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformIdempotencyRecordDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformIdempotencyRecordMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.query.IdempotencyScopeQuery;
import cn.iocoder.yudao.module.pms.platform.support.service.OperationExecutionStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 统一幂等执行存储的平台实现：复用既有平台幂等台账表与 insertIfAbsent 语义，
 * 回执 JSON 存入响应载荷列；必须在调用方业务事务内执行。
 */
@Component
public class PlatformOperationExecutionStore implements OperationExecutionStore {

    static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    static final String STATUS_COMPLETED = "COMPLETED";

    private final PlatformIdempotencyRecordMapper mapper;

    public PlatformOperationExecutionStore(PlatformIdempotencyRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reserve(OperationExecutionKey key, String requestDigest) {
        PlatformIdempotencyRecordDO record = new PlatformIdempotencyRecordDO();
        record.setTenantId(key.tenantId());
        record.setScopeCode(key.scopeCode());
        record.setActorId(key.actorId());
        record.setIdempotencyKey(key.idempotencyKey());
        record.setRequestDigest(requestDigest);
        record.setStatus(STATUS_IN_PROGRESS);
        record.setVersion(0);
        return mapper.insertIfAbsent(record) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<StoredExecution> findExisting(OperationExecutionKey key) {
        PlatformIdempotencyRecordDO existing = mapper.selectByScope(new IdempotencyScopeQuery(
                key.tenantId(), key.scopeCode(), key.actorId(), key.idempotencyKey()));
        if (existing == null) {
            return Optional.empty();
        }
        BusinessOperationReceipt receipt = existing.getResponsePayload() == null ? null
                : JsonUtils.parseObject(existing.getResponsePayload(), BusinessOperationReceipt.class);
        return Optional.of(new StoredExecution(existing.getRequestDigest(), existing.getStatus(), receipt));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(OperationExecutionKey key, String aggregateType, String resourceKey,
                         BusinessOperationReceipt receipt) {
        PlatformIdempotencyRecordDO existing = mapper.selectByScope(new IdempotencyScopeQuery(
                key.tenantId(), key.scopeCode(), key.actorId(), key.idempotencyKey()));
        if (existing == null) {
            throw new IllegalStateException("幂等预约记录缺失，无法完成: " + key.idempotencyKey());
        }
        PlatformIdempotencyRecordDO completed = new PlatformIdempotencyRecordDO();
        completed.setId(existing.getId());
        completed.setStatus(STATUS_COMPLETED);
        completed.setResourceType(aggregateType);
        completed.setResourceKey(resourceKey);
        completed.setResponsePayload(JsonUtils.toJsonString(receipt));
        if (mapper.updateById(completed) != 1) {
            throw new IllegalStateException("幂等成功事实写入失败: " + key.idempotencyKey());
        }
    }
}
