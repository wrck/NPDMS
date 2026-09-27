package cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query;

public record RequirementActivationUpdate(Long tenantId, Long revisionId, Long expectedVersion, String updater) {}
