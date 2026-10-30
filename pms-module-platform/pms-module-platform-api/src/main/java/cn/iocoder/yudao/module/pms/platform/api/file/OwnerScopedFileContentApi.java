package cn.iocoder.yudao.module.pms.platform.api.file;

/** Internal bounded content read. The registered Owner must authorize an exact immutable reference. */
public interface OwnerScopedFileContentApi {
    record Query(String ownerCode, String businessKey, Long referenceId) { }
    record Content(String name, String mediaType, byte[] bytes) { }
    Content read(Query query);
}
