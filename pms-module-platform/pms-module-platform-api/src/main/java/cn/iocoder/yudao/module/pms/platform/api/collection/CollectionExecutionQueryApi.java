package cn.iocoder.yudao.module.pms.platform.api.collection;

/** 按业务身份读取单次真实采集记录；凭证不属于结果投影。 */
public interface CollectionExecutionQueryApi {
    CollectionApplicationApi.Execution get(Query query);
    record Query(Long tenantId, Long actorId, String entry, Long objectId, Long executionId) { }
}
