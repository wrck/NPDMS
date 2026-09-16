package cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query;

public record RequirementFreezeUpdate(Long tenantId, Long revisionId, Integer expectedVersion, Long actorId, java.time.LocalDateTime frozenAt) {}
