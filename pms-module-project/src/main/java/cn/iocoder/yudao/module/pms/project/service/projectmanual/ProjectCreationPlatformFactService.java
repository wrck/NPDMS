package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.platform.PlatformIdempotencyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.platform.PlatformOperationAuditDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.platform.PlatformOutboxEventDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.platform.PlatformIdempotencyRecordMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.platform.PlatformOperationAuditMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.platform.PlatformOutboxEventMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** 将项目写命令、幂等成功、审计和Outbox事件封装在同一事务中。 */
@Service
public class ProjectCreationPlatformFactService {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String OUTBOX_STATUS_PENDING = "PENDING";

    @Resource
    private PlatformIdempotencyRecordMapper idempotencyMapper;
    @Resource
    private PlatformOperationAuditMapper auditMapper;
    @Resource
    private PlatformOutboxEventMapper outboxMapper;

    private final Clock clock = Clock.systemDefaultZone();

    @Transactional(rollbackFor = Exception.class)
    public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest,
                                          Class<T> responseType, Supplier<T> operation,
                                          Function<T, SuccessFacts> successFactsFactory) {
        validate(scope, requestDigest, responseType, operation, successFactsFactory);
        PlatformIdempotencyRecordDO reservation = reservation(scope, requestDigest);
        if (idempotencyMapper.insertIfAbsent(reservation) == 0) {
            return decideExisting(scope, requestDigest, responseType);
        }

        T response = operation.get();
        SuccessFacts facts = successFactsFactory.apply(response);
        persistSuccess(reservation, scope, response, facts);
        return new ExecutionResult<>(Decision.NEW, response);
    }

    private <T> ExecutionResult<T> decideExisting(IdempotencyScope scope, String requestDigest,
                                                   Class<T> responseType) {
        PlatformIdempotencyRecordDO existing = idempotencyMapper.selectByScope(
                scope.tenantId(), scope.scopeCode(), scope.actorId(), scope.key());
        if (existing == null) {
            return new ExecutionResult<>(Decision.IN_PROGRESS, null);
        }
        if (!requestDigest.equals(existing.getRequestDigest())) {
            return new ExecutionResult<>(Decision.CONFLICT, null);
        }
        if (STATUS_IN_PROGRESS.equals(existing.getStatus())) {
            return new ExecutionResult<>(Decision.IN_PROGRESS, null);
        }
        if (!STATUS_COMPLETED.equals(existing.getStatus()) || existing.getResponsePayload() == null) {
            return new ExecutionResult<>(Decision.IN_PROGRESS, null);
        }
        return new ExecutionResult<>(Decision.REPLAY_COMPLETED,
                JsonUtils.parseObject(existing.getResponsePayload(), responseType));
    }

    private <T> void persistSuccess(PlatformIdempotencyRecordDO reservation, IdempotencyScope scope,
                                    T response, SuccessFacts facts) {
        if (facts == null || isBlank(facts.operationCode()) || isBlank(facts.aggregateType())
                || isBlank(facts.resourceKey()) || isBlank(facts.correlationId())
                || facts.detailSnapshot() == null || isBlank(facts.eventType()) || facts.eventPayload() == null) {
            throw new IllegalArgumentException("项目写命令成功事实不完整");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        PlatformIdempotencyRecordDO completed = new PlatformIdempotencyRecordDO();
        completed.setId(reservation.getId());
        completed.setStatus(STATUS_COMPLETED);
        completed.setResourceType(facts.aggregateType());
        completed.setResourceKey(facts.resourceKey());
        completed.setResponsePayload(JsonUtils.toJsonString(response));
        if (idempotencyMapper.updateById(completed) != 1) {
            throw new IllegalStateException("平台幂等成功事实写入失败");
        }

        PlatformOperationAuditDO audit = new PlatformOperationAuditDO();
        audit.setTenantId(scope.tenantId());
        audit.setOperationCode(facts.operationCode());
        audit.setAggregateType(facts.aggregateType());
        audit.setAggregateKey(facts.resourceKey());
        audit.setActorId(scope.actorId());
        audit.setCorrelationId(facts.correlationId());
        audit.setIdempotencyKeyDigest(sha256(scope.key()));
        audit.setResultCode("SUCCESS");
        audit.setDetailSnapshot(facts.detailSnapshot());
        audit.setOccurredAt(now);
        audit.setCreateTime(now);
        if (auditMapper.insert(audit) != 1) {
            throw new IllegalStateException("平台操作审计写入失败");
        }

        PlatformOutboxEventDO outbox = new PlatformOutboxEventDO();
        outbox.setTenantId(scope.tenantId());
        outbox.setEventId(UUID.randomUUID().toString());
        outbox.setEventType(facts.eventType());
        outbox.setAggregateType(facts.aggregateType());
        outbox.setAggregateKey(facts.resourceKey());
        outbox.setPayload(facts.eventPayload());
        outbox.setStatus(OUTBOX_STATUS_PENDING);
        outbox.setOccurredAt(now);
        outbox.setRetryCount(0);
        if (outboxMapper.insert(outbox) != 1) {
            throw new IllegalStateException("平台Outbox事件写入失败");
        }
    }

    private PlatformIdempotencyRecordDO reservation(IdempotencyScope scope, String requestDigest) {
        PlatformIdempotencyRecordDO record = new PlatformIdempotencyRecordDO();
        record.setTenantId(scope.tenantId());
        record.setScopeCode(scope.scopeCode());
        record.setActorId(scope.actorId());
        record.setIdempotencyKey(scope.key());
        record.setRequestDigest(requestDigest);
        record.setStatus(STATUS_IN_PROGRESS);
        record.setVersion(0);
        return record;
    }

    private void validate(IdempotencyScope scope, String requestDigest, Class<?> responseType,
                          Supplier<?> operation, Function<?, SuccessFacts> successFactsFactory) {
        if (scope == null || scope.tenantId() == null || scope.scopeCode() == null
                || scope.scopeCode().isBlank() || scope.scopeCode().length() > 128
                || scope.actorId() == null || scope.key() == null || scope.key().isBlank()
                || scope.key().length() > 128 || requestDigest == null
                || !requestDigest.matches("[0-9a-f]{64}") || responseType == null
                || operation == null || successFactsFactory == null) {
            throw new IllegalArgumentException("平台幂等执行参数不完整");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum Decision { NEW, REPLAY_COMPLETED, CONFLICT, IN_PROGRESS }

    public record IdempotencyScope(Long tenantId, String scopeCode, Long actorId, String key) {
    }

    public record SuccessFacts(String operationCode, String aggregateType, String resourceKey,
                               String correlationId, String detailSnapshot,
                               String eventType, String eventPayload) {
    }

    public record ExecutionResult<T>(Decision decision, T response) {
    }
}
