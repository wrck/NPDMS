package cn.iocoder.yudao.module.pms.platform.api.command;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 平台命令幂等与审计同事务执行契约；存在业务事件时同时写入Outbox。
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
                        String eventType, String eventPayload,
                        List<BusinessEvent> businessEvents) {

        public SuccessFacts {
            businessEvents = businessEvents == null ? List.of() : List.copyOf(businessEvents);
        }

        public SuccessFacts(String operationCode, String aggregateType, String resourceKey,
                            String correlationId, String detailSnapshot,
                            String eventType, String eventPayload) {
            this(operationCode, aggregateType, resourceKey, correlationId, detailSnapshot,
                    eventType, eventPayload, List.of());
        }

        public SuccessFacts(String operationCode, String aggregateType, String resourceKey,
                            String correlationId, String detailSnapshot,
                            List<BusinessEvent> businessEvents) {
            this(operationCode, aggregateType, resourceKey, correlationId, detailSnapshot,
                    null, null, businessEvents);
        }
    }

    record BusinessEvent(String eventId, String eventType, String eventPayload) {
    }

    record ExecutionResult<T>(Decision decision, T response) {
    }
}
