package cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query;

/** The target identity is supplied by verified migration evidence, never inferred from an SN. */
public record DeviceFactoryVersionRecordIdentityQuery(Long tenantId, Long projectId, Long id) {}
