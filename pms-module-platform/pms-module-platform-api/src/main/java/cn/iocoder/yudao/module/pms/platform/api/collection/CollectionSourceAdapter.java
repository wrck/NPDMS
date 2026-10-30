package cn.iocoder.yudao.module.pms.platform.api.collection;

import lombok.Builder;

/** Business owner extension. Register a bean only when the source is actually implemented and authorized. */
public interface CollectionSourceAdapter {
    String entry();
    Source authorize(AuthorizationQuery query);

    /** Called under the source and credential locks, with the actual newly authorized connection; never carries a secret. */
    default void authorizeConnection(PreparedConnection query) { }
    @Builder
    record PreparedConnection(AuthorizationQuery authorization, Source source, String host, Integer port,
                              String protocol, String username, Long credentialId, Long credentialVersion, Long grantId) { }

    @Builder
    record AuthorizationQuery(Long tenantId, Long actorId, Long objectId, Long deviceId,
                              Access access, Long expectedVersion) { }

    enum Access { READ, EXECUTE, CANCEL, CONSUME }
    @Builder
    record Source(String entry, Long objectId, String sourceContext, String sourceObjectType,
                  Long projectId, Long deviceId, String deviceName, Long version,
                  boolean manualAllowed, boolean canExecute, String completionMode, String title,
                  CollectionExecutionProfile executionProfile) {
        public Source(String entry, Long objectId, String sourceContext, String sourceObjectType, Long projectId,
                      Long deviceId, String deviceName, Long version, boolean manualAllowed, boolean canExecute,
                      String completionMode, String title) {
            this(entry, objectId, sourceContext, sourceObjectType, projectId, deviceId, deviceName, version,
                    manualAllowed, canExecute, completionMode, title, null);
        }
    }
}
