package cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query;

public record RequirementRevisionPageQuery(Long tenantId, Long entityId, Integer beforeRevisionNo, int limit) {}
