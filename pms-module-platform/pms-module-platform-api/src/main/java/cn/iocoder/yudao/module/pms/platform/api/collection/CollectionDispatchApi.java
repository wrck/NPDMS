package cn.iocoder.yudao.module.pms.platform.api.collection;
import java.util.List;
import java.util.Map;
/** Internal owner API. Business callers must authorize the source object before calling. */
public interface CollectionDispatchApi {
    void dispatchManual(Command command);
    void cancel(Long tenantId, String platformTaskId);
    default void dispatchSaved(SavedCommand command) { throw new UnsupportedOperationException("Saved dispatch unavailable"); }
    default List<Map<String, Object>> semanticResults(Long tenantId, String platformTaskId) {
        throw new UnsupportedOperationException("Semantic results unavailable");
    }
    record Command(Long tenantId, String platformTaskId, List<String> commands,
                   String username, char[] secret, String traceId, CollectionExecutionProfile executionProfile) {
        public Command(Long tenantId, String platformTaskId, List<String> commands, String username, char[] secret, String traceId) {
            this(tenantId, platformTaskId, commands, username, secret, traceId, null);
        }
        @Override public String toString() { return "CollectionDispatchCommand[credential material omitted]"; }
    }
    record SavedCommand(Long tenantId, String platformTaskId, List<String> commands,
                        String username, String connectionId, long connectionVersion, String traceId, CollectionExecutionProfile executionProfile) {
        public SavedCommand(Long tenantId, String platformTaskId, List<String> commands, String username, String connectionId, long connectionVersion, String traceId) {
            this(tenantId, platformTaskId, commands, username, connectionId, connectionVersion, traceId, null);
        }
    }
}
