package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query;
public record CollectionRequestPageQuery(Long tenantId, String entry, Long objectId, int pageNo, int pageSize) { }
