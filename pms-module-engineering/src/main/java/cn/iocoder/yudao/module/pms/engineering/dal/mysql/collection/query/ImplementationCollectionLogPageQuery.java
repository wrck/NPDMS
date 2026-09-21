package cn.iocoder.yudao.module.pms.engineering.dal.mysql.collection.query;

public record ImplementationCollectionLogPageQuery(Long tenantId, String entry, Long objectId,
                                                  Long projectId, Long deviceId, int pageNo, int pageSize) { }
