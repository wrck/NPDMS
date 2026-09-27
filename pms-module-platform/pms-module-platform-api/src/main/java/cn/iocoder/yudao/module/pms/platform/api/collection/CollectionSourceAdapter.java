package cn.iocoder.yudao.module.pms.platform.api.collection;

/** Business owner extension. Register a bean only when the source is actually implemented and authorized. */
public interface CollectionSourceAdapter {
    String entry();
    Source authorize(Long tenantId, Long actorId, Long objectId, Long deviceId, Access access, Long expectedVersion);
    enum Access { READ, EXECUTE, CANCEL, CONSUME }
    record Source(String entry, Long objectId, String sourceContext, String sourceObjectType,
                  Long projectId, Long deviceId, String deviceName, Long version,
                  boolean manualAllowed, boolean canExecute, String completionMode, String title) { }
}
