package cn.iocoder.yudao.module.pms.platform.dal.mysql.businessview.query;
/** PM-03: exact tenant-scoped revision lookup. */
public record BusinessViewRowQuery(Long tenantId, Long revisionId) { }
