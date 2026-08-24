package cn.iocoder.yudao.module.pms.platform.api.command;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 平台命令幂等、审计和Outbox同事务执行契约。
 */
public interface PlatformCommandExecutionApi {

    <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest,
                                   Class<T> responseType, Supplier<T> operation,
                                   Function<T, SuccessFacts> successFactsFactory);

    enum Decision { NEW, REPLAY_COMPLETED, CONFLICT, IN_PROGRESS }

    record IdempotencyScope(Long tenantId, String scopeCode, Long actorId, String key) {
    }

    record SuccessFacts(String operationCode, String aggregateType, String resourceKey,
                        String correlationId, String detailSnapshot,
                        String eventType, String eventPayload) {
    }

    record ExecutionResult<T>(Decision decision, T response) {
    }
}
