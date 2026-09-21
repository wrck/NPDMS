package cn.iocoder.yudao.module.pms.platform.api.collection;
import java.util.List;
/** Internal owner API. Business callers must authorize the source object before calling. */
public interface CollectionDispatchApi {
    void dispatchManual(Command command);
    void cancel(Long tenantId, String platformTaskId);
    default void dispatchSaved(SavedCommand command) { throw new UnsupportedOperationException("Saved dispatch unavailable"); }
    record Command(Long tenantId, String platformTaskId, List<String> commands,
                   String username, char[] secret, String traceId) { }
    record SavedCommand(Long tenantId, String platformTaskId, List<String> commands,
                        String username, String connectionId, long connectionVersion, String traceId) { }
}
