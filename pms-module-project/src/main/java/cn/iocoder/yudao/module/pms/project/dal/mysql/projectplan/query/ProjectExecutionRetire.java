package cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query;

public record ProjectExecutionRetire(Long tenantId, Long projectId, Long executionId, Integer expectedVersion) { }
