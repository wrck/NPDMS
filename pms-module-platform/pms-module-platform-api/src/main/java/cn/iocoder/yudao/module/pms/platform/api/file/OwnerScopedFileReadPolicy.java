package cn.iocoder.yudao.module.pms.platform.api.file;

/** Owner authorization is rechecked before and after reading; this SPI grants no public file actions. */
public interface OwnerScopedFileReadPolicy {
    String ownerCode();
    record Query(Long tenantId, Long actorId, String businessKey, FileEvidenceApi.Document document) { }
    void require(Query query);
}
