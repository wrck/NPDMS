package cn.iocoder.yudao.module.pms.platform.api.collection;
/** Issues an existing file-platform access ticket; applies file and business scope authorization. */
public interface CollectionLogAccessApi {
    String download(Long tenantId, Long actorId, String platformTaskId);

    /** Reads a bounded, exact callback file after current source and file authorization. */
    byte[] read(ContentQuery query);
    record ContentQuery(Long tenantId, Long actorId, String platformTaskId, Long resultVersion, Long fileVersionId) { }
}
