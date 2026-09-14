package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

public record ProjectTaskExecutionLookupQuery(Long tenantId, Long projectId, Long taskId, Long executionContractId) { }
