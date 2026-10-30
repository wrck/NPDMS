package cn.iocoder.yudao.module.pms.integration.api.deviceops;

/** Resource operations keep saved secrets inside DAC; callers receive metadata only. */
public interface DeviceOpsResourceApi {
    Connection saveConnection(ConnectionCommand command);
    Connection getConnection(String id);
    void registerScript(String key, String version, String content, String sha256);

    record Connection(String id, String host, int port, String protocol, String username, long version) { }
    record ConnectionCommand(String id, String name, String host, int port, String protocol, String username, char[] secret) { }
    final class ResourceException extends IllegalStateException {
        public ResourceException(String safeMessage) { super(safeMessage); }
    }
}
