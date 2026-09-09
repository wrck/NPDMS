package cn.iocoder.yudao.module.pms.platform.dal.mysql.businessview.query;
/** PM-03: stable identity, ascending revisions is the common writer lock order. */
public record BusinessViewIdentityQuery(Long tenantId, String entityType, String viewKey) { }
