package cn.iocoder.yudao.module.pms.platform.api.collection;

import java.util.Set;

/** Business owner receives an immutable log reference. Repeated delivery must be idempotent. */
public interface CollectionBusinessResultReceiver {
    Set<String> entries();

    /** Persist the source-bound result, or throw without accepting it. Never advance business lifecycle state. */
    void receive(Result result);

    record Result(Long tenantId, String entry, Long objectId, String sourceContext, String sourceObjectType,
                  Long projectId, Long deviceId, Long executionId, Long actorId, String platformTaskId,
                  Long resultVersion, Long fileVersionId, String protocol, String externalStatus,
                  String failureCategory, String commandText, String templateName) { }
}
