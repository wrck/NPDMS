package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query;

public record DeviceLogParsePageQuery(Long tenantId, Long projectId, String sourceEntry, Long sourceObjectId,
                                     Long sourceExecutionId, int pageNo, int pageSize) { }
