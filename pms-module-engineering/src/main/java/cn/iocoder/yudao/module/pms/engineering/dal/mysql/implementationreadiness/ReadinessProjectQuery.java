package cn.iocoder.yudao.module.pms.engineering.dal.mysql.implementationreadiness;

public record ReadinessProjectQuery(Long tenantId, Long projectId, boolean lock) { }
