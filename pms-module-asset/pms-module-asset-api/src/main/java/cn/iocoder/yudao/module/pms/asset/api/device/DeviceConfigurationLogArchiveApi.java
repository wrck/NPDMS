package cn.iocoder.yudao.module.pms.asset.api.device;

/** Configuration log archive only; receipt or archive success is not configuration business completion. */
public interface DeviceConfigurationLogArchiveApi {
    void receiveCollection(CollectionLog command);
    record CollectionLog(Long tenantId, Long actorId, Long projectId, Long deviceId,
                         String platformTaskId, Long resultVersion, Long fileVersionId) { }
}
