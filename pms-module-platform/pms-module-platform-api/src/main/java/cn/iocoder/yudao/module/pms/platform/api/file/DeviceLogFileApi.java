package cn.iocoder.yudao.module.pms.platform.api.file;

/** Bounded internal bridge to PLT storage; collection origins are revalidated before read or write. */
public interface DeviceLogFileApi {
    byte[] readCollection(Origin origin);
    NativeGeneratedFileApi.RegisteredFile store(Command command);
    record Origin(Long tenantId, Long actorId, Long projectId, Long deviceId,
                  String platformTaskId, Long resultVersion, Long fileVersionId) { }
    record Command(Long tenantId, Long actorId, String operationId, Long logFileId,
                   String fileName, byte[] content, Origin origin) {
        public Command { content = content == null ? null : content.clone(); }
        @Override public byte[] content() { return content == null ? null : content.clone(); }
    }
}
