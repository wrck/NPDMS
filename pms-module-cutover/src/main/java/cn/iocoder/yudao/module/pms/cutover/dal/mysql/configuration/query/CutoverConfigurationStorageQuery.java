package cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query;

/** 已锁定配置草稿的精确租户/修订范围。 */
public record CutoverConfigurationStorageQuery(Long tenantId, Long configurationRevisionId) { }
