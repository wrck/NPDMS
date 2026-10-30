package cn.iocoder.yudao.module.infra.api.file;

/** Internal receipt read, bounded by the caller's limit and the platform's 50 MiB maximum.
 * The caller must authorize its FileVersion before supplying this identity. */
public interface FileStorageContentApi {
    byte[] read(Query query);
    record Query(Long tenantId, Long infraFileId, Long expectedSize, String expectedSha256, int maxBytes) { }
}
