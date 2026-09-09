package cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.query;
/** PM-03: trusted tenant, one query intent. */
public record DeliveryDefinitionIdentityQuery(Long tenantId, String definitionKind, String definitionCode) { }
