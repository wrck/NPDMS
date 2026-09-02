package cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query;

public record ExportTaskIdentityQuery(Long tenantId, String ownerContext, String exportType,
                                      Long actorUserId, String operationId) {
}
