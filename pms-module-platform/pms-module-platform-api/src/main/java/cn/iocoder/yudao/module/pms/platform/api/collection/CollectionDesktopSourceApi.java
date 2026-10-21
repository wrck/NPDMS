package cn.iocoder.yudao.module.pms.platform.api.collection;

import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskDTO;

/** INT reads an already-authorized immutable command and reports provider acceptance through PLT. */
public interface CollectionDesktopSourceApi {
    Snapshot snapshot(TaskKey query);
    void accepted(Acceptance command);

    record TaskKey(Long tenantId, String platformTaskId) { }
    record Snapshot(CollectionTaskDTO task, Long actorId, String username, String commandText,
                    String localConnectionId, Long localConnectionVersion, CollectionExecutionProfile executionProfile) { }
    record Acceptance(TaskKey task, String collectionId, String externalStatus) { }
}
