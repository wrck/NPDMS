package cn.iocoder.yudao.module.infra.api.file;

/** Internal bounded receipt read. The caller must authorize its FileVersion before supplying this identity. */
public interface FileStorageContentApi {
    byte[] read(Query query);
    record Query(Long tenantId, Long infraFileId, Long expectedSize, String expectedSha256, int maxBytes) { }
}
