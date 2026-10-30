package cn.iocoder.yudao.framework.common.util.authorization;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Explicit, synchronous read-only authorization reuse; never propagated to another thread. */
public final class AuthorizationReadScope implements AutoCloseable {
    private static final ThreadLocal<AuthorizationReadScope> CURRENT = new ThreadLocal<>();
    private final AuthorizationReadScope previous;
    private final Map<Object, Object> values = new HashMap<>();

    private AuthorizationReadScope() {
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    public static AuthorizationReadScope open() {
        return new AuthorizationReadScope();
    }

    @SuppressWarnings("unchecked")
    public static <T> T read(Object key, Supplier<T> loader) {
        AuthorizationReadScope scope = CURRENT.get();
        if (scope == null) return loader.get();
        if (scope.values.containsKey(key)) return (T) scope.values.get(key);
        T value = loader.get(); // Exceptions retain the original caller's retry / failure policy.
        scope.values.put(key, value);
        return value;
    }

    @Override
    public void close() {
        values.clear();
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
