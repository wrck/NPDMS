package cn.iocoder.yudao.module.pms.cutover.support;

import cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandExecutor;
import cn.iocoder.yudao.module.pms.cutover.service.support.CutoverWriteIntent;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** In-memory platform contract for unit tests; no database or external writes. */
public class CutoverTestCommands implements PlatformCommandExecutionApi {
    private final Map<IdempotencyScope, Cached> completed = new HashMap<>();

    public static CutoverWriteIntent intent() {
        return new CutoverWriteIntent(UUID.randomUUID().toString(), "cutover-unit-test");
    }

    public static CutoverCommandExecutor executor() {
        return new CutoverCommandExecutor(new CutoverTestCommands());
    }

    @Override
    public <T> ExecutionResult<T> execute(IdempotencyScope scope, String digest, Class<T> type,
                                         Supplier<T> operation, Function<T, SuccessFacts> facts) {
        Cached existing = completed.get(scope);
        if (existing != null) {
            return existing.digest().equals(digest)
                    ? new ExecutionResult<>(Decision.REPLAY_COMPLETED, type.cast(existing.response()))
                    : new ExecutionResult<>(Decision.CONFLICT, null);
        }
        T result = operation.get();
        facts.apply(result);
        completed.put(scope, new Cached(digest, result));
        return new ExecutionResult<>(Decision.NEW, result);
    }

    private record Cached(String digest, Object response) { }
}
