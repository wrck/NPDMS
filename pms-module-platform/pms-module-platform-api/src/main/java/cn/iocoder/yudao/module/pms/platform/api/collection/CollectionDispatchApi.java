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
                   String username, char[] secret, String traceId) { }
    record SavedCommand(Long tenantId, String platformTaskId, List<String> commands,
                        String username, String connectionId, long connectionVersion, String traceId) { }
}
