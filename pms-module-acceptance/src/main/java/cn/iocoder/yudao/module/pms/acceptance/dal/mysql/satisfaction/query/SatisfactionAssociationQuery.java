package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query;

public record SatisfactionAssociationQuery(Long tenantId, Long projectId, Long afterId, int limit) { }
