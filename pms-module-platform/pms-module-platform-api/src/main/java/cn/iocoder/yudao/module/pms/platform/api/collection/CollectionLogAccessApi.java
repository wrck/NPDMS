package cn.iocoder.yudao.module.pms.platform.api.collection;
/** Issues an existing file-platform access ticket; applies file and business scope authorization. */
public interface CollectionLogAccessApi {
    String download(Long tenantId, Long actorId, String platformTaskId);
}
